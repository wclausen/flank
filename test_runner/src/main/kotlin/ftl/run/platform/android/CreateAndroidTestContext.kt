package ftl.run.platform.android

import com.google.common.annotations.VisibleForTesting
import com.linkedin.dex.parser.DecodedValue
import com.linkedin.dex.parser.DexParser
import com.linkedin.dex.parser.TestAnnotation
import com.linkedin.dex.parser.TestMethod
import com.linkedin.dex.parser.formatClassName
import com.linkedin.dex.parser.getAnnotationsDirectory
import com.linkedin.dex.parser.getClassAnnotationValues
import com.linkedin.dex.spec.ClassDefItem
import com.linkedin.dex.spec.DexFile
import flank.common.logLn
import ftl.args.AndroidArgs
import ftl.args.ArgsHelper
import ftl.args.CalculateShardsResult
import ftl.config.FtlConstants
import ftl.filter.TestFilter
import ftl.filter.TestFilters
import ftl.run.model.AndroidTestContext
import ftl.run.model.AndroidTestShards
import ftl.run.model.InstrumentationTestContext
import ftl.shard.Chunk
import ftl.shard.createShardsByTestForShards
import ftl.util.FileReference
import ftl.util.FlankTestMethod
import ftl.util.downloadIfNeeded
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import ftl.shard.TestMethod as ShardTestMethod

suspend fun AndroidArgs.createAndroidTestContexts(): List<AndroidTestContext> = resolveApks().setupShards(this)

private suspend fun List<AndroidTestContext>.setupShards(
    args: AndroidArgs,
    testFilter: TestFilter = TestFilters.fromTestTargets(args.testTargets, args.testTargetsForShard)
): List<AndroidTestContext> = coroutineScope {
    map { testContext ->
        async {
            when {
                testContext !is InstrumentationTestContext -> testContext
                args.useCustomSharding -> testContext.userShards(args.customSharding)
                args.useTestTargetsForShard ->
                    testContext.downloadApks()
                        .calculateDummyShards(args, testFilter)
                else -> testContext.downloadApks().calculateShards(args, testFilter)
            }
        }
    }.awaitAll().dropEmptyInstrumentationTest()
}

private fun InstrumentationTestContext.userShards(customShardingMap: Map<String, AndroidTestShards>) = customShardingMap
    .values
    .firstOrNull { app.hasReference(it.app) && test.hasReference(it.test) }
    ?.let { customSharding ->
        copy(
            shards = customSharding.shards
                .map { methods -> Chunk(methods.value.map(::ShardTestMethod)) },
            ignoredTestCases = customSharding.junitIgnored
        )
    }
    ?: this

private fun FileReference.hasReference(path: String) = local == path || gcs == path

private val AndroidArgs.useCustomSharding: Boolean
    get() = customSharding.isNotEmpty()

private val AndroidArgs.useTestTargetsForShard: Boolean
    get() = testTargetsForShard.isNotEmpty()

private fun InstrumentationTestContext.downloadApks(): InstrumentationTestContext = copy(
    app = app.downloadIfNeeded(),
    test = test.downloadIfNeeded()
)

private fun InstrumentationTestContext.calculateShards(
    args: AndroidArgs,
    testFilter: TestFilter = TestFilters.fromTestTargets(args.testTargets, args.testTargetsForShard)
): InstrumentationTestContext = ArgsHelper.calculateShards(
    filteredTests = getFlankTestMethods(testFilter),
    args = args,
    forcedShardCount = args.numUniformShards
).run {
    copy(
        shards = shardChunks.filter { it.testMethods.isNotEmpty() },
        ignoredTestCases = ignoredTestCases
    )
}

private fun InstrumentationTestContext.calculateDummyShards(
    args: AndroidArgs,
    testFilter: TestFilter = TestFilters.fromTestTargets(args.testTargets, args.testTargetsForShard),
): InstrumentationTestContext {
    val filteredTests = getFlankTestMethods(testFilter)
    val shardsResult = if (filteredTests.isEmpty()) {
        CalculateShardsResult(emptyList(), emptyList())
    } else {
        CalculateShardsResult(
            shardChunks = ArgsHelper.testMethodsAlwaysRun(args.createShardsByTestForShards(), args),
            ignoredTestCases = emptyList()
        )
    }
    return copy(
        shards = shardsResult.shardChunks.filter { it.testMethods.isNotEmpty() },
        ignoredTestCases = shardsResult.ignoredTestCases
    )
}

@VisibleForTesting
internal fun InstrumentationTestContext.getFlankTestMethods(
    testFilter: TestFilter
): List<FlankTestMethod> =
    getParametrizedClasses().let { parameterizedClasses: List<String> ->
        DexParser.findTestMethods(test.local).asSequence()
            .distinctBy { it.testName }
            .filter(testFilter.shouldRun)
            .filterNot(parameterizedClasses::belong)
            .map(TestMethod::toFlankTestMethod).toList()
            .plus(parameterizedClasses.onlyShouldRun(testFilter))
    }

private fun List<String>.belong(method: TestMethod) = any { className -> method.testName.startsWith(className) }

private fun List<String>.onlyShouldRun(filter: TestFilter) = this
    .filter { filter.shouldRun(TestMethod(it, emptyList())) }
    .map { FlankTestMethod("class $it", ignored = false, isParameterizedClass = true) }

private fun TestMethod.toFlankTestMethod() = FlankTestMethod(
    testName = "class $testName",
    ignored = annotations.any { it.name in ignoredAnnotations }
)

private val ignoredAnnotations = listOf(
    "org.junit.Ignore",
    "androidx.test.filters.Suppress",
    "android.support.test.filters.Suppress"
)

@VisibleForTesting
internal fun InstrumentationTestContext.getParametrizedClasses(): List<String> =
    DexParser.readDexFiles(test.local).fold(emptyList()) { accumulator, file: DexFile ->
        accumulator + file.classDefs
            .filter(file::isParametrizedClass)
            .map(file::formatClassName) // returns class name + '#'
            .map { it.dropLast(1) } // so drop '#'
    }

private fun DexFile.isParametrizedClass(classDef: ClassDefItem): Boolean =
    getClassAnnotationValues(getAnnotationsDirectory(classDef)).let { annotations: List<TestAnnotation> ->
        annotations.any { it.name.contains("RunWith", ignoreCase = true) } && annotations
            .flatMap { it.values.values }
            .filterIsInstance<DecodedValue.DecodedType>()
            .any { it.isParameterizedAnnotation() }
    }

private fun DecodedValue.DecodedType.isParameterizedAnnotation(): Boolean =
    parameterizedTestRunners.any { runner ->
        value.contains(runner, ignoreCase = true)
    }

private val parameterizedTestRunners = listOf("JUnitParamsRunner", "Parameterized")

private fun List<AndroidTestContext>.dropEmptyInstrumentationTest(): List<AndroidTestContext> =
    filterIsInstance<InstrumentationTestContext>().filter { it.shards.isEmpty() }.let { withoutTests ->
        if (withoutTests.isNotEmpty())
            printNoTests(withoutTests)
        minus(withoutTests)
    }

private fun printNoTests(testApks: List<InstrumentationTestContext>) {
    val testApkNames = testApks.joinToString(", ") { pathname -> File(pathname.test.local).name }
    logLn("${FtlConstants.indent}No tests for $testApkNames")
    logLn()
}
