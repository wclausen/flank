package ftl.domain

import flank.common.logLn
import ftl.api.Platform
import ftl.api.fetchIpBlocks
import ftl.api.fetchNetworkProfiles
import ftl.api.fetchOrientation
import ftl.api.fetchSoftwareCatalog
import ftl.args.IosArgs
import ftl.environment.common.toCliTable
import ftl.environment.toCliTable
import ftl.ios.IosCatalog
import java.nio.file.Paths

interface DescribeIosTestEnvironment {
    val configPath: String
}

operator fun DescribeIosTestEnvironment.invoke() {
    val projectId = IosArgs.loadOrDefault(Paths.get(configPath)).project
    logLn(IosCatalog.devicesCatalogAsTable(projectId))
    logLn(IosCatalog.softwareVersionsAsTable(projectId))
    logLn(IosCatalog.localesAsTable(projectId))
    logLn(fetchSoftwareCatalog().toCliTable())
    logLn(fetchNetworkProfiles().toCliTable())
    // TODO move toCliTable() and printing presentation layer during refactor of presentation after #1728
    logLn(fetchOrientation(projectId, Platform.IOS).toCliTable())
    logLn(fetchIpBlocks().toCliTable())
}
