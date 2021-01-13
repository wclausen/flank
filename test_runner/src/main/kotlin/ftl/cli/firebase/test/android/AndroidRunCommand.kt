package ftl.cli.firebase.test.android

import com.fasterxml.jackson.core.type.TypeReference
import flank.common.logLn
import ftl.analytics.Segment
import ftl.args.AndroidArgs
import ftl.args.ArgsHelper
import ftl.args.setupLogLevel
import ftl.args.validate
import ftl.cli.firebase.test.CommonRunCommand
import ftl.config.FtlConstants
import ftl.config.android.AndroidFlankConfig
import ftl.config.android.AndroidGcloudConfig
import ftl.config.createConfiguration
import ftl.mock.MockServer
import ftl.run.ANDROID_SHARD_FILE
import ftl.run.dumpShards
import ftl.run.newTestRun
import ftl.util.DEVICE_SYSTEM
import ftl.util.TEST_TYPE
import ftl.util.setCrashReportTag
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Paths

@Command(
    name = "run",
    sortOptions = false,
    headerHeading = "",
    synopsisHeading = "%n",
    descriptionHeading = "%n@|bold,underline Description:|@%n%n",
    parameterListHeading = "%n@|bold,underline Parameters:|@%n",
    optionListHeading = "%n@|bold,underline Options:|@%n",
    header = ["Run tests on Firebase Test Lab"],
    description = [
        """Uploads the app and test apk to GCS.
Runs the espresso tests using orchestrator.
Configuration is read from flank.yml
"""
    ],
    usageHelpAutoWidth = true
)
class AndroidRunCommand : CommonRunCommand(), Runnable {

    @CommandLine.Mixin
    private val androidGcloudConfig = AndroidGcloudConfig()

    @CommandLine.Mixin
    private val androidFlankConfig = AndroidFlankConfig()

    override val config by createConfiguration(androidGcloudConfig, androidFlankConfig)

    init {
        configPath = FtlConstants.defaultAndroidConfig
    }

    fun x() {
    }

    override fun run() {
        if (dryRun) {
            MockServer.start()
        }

        Segment.identifyUser("test number 1")
        AndroidArgs.load(Paths.get(configPath), cli = this).apply {
            setupLogLevel()
            logLn(this)
            setCrashReportTag(
                DEVICE_SYSTEM to "android",
                TEST_TYPE to type?.name.orEmpty()
            )
        }.validate().run {
            sendConfiguration()
            runBlocking {
                if (dumpShards) dumpShards()
                else newTestRun()
            }
        }
    }

    fun AndroidArgs.sendConfiguration() {
        val defaultArgs = AndroidArgs.default()
        val defaultArgsMap = defaultArgs.objectToMap()
        val defaultCommonArgs = defaultArgs.commonArgs.objectToMap()
        objectToMap().filter { it.key != "commonArgs" }.getNonDefaultArgs(defaultArgsMap)
            .plus(commonArgs.objectToMap().getNonDefaultArgs(defaultCommonArgs))
            .let {
                it.forEach { (key, value) ->
                    println("Config $key: $value")
                }
                Segment.logConfiguration(it)
            }
    }
    private fun Map<String, Any>.getNonDefaultArgs(defaultArgs: Map<String, Any>) =
        keys.fold(mapOf<String, Any?>()) { acc, key ->
            acc.compareValues(key, this, defaultArgs[key])
        }
    private fun Map<String, Any?>.compareValues(key: String, source: Map<String, Any>, defaultValue: Any?) =
        if (source[key] != defaultValue) this + (key to source[key])
        else this
    private fun Any.objectToMap() = ArgsHelper.yamlMapper.convertValue(this, object : TypeReference<Map<String, Any>>() {})

    @Option(
        names = ["--dump-shards"],
        description = ["Measures test shards from given test apks and writes them into $ANDROID_SHARD_FILE file instead of executing."]
    )
    var dumpShards: Boolean = false
}
