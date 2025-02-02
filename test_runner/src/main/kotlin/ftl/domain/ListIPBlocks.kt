package ftl.domain

import flank.common.logLn
import ftl.api.fetchIpBlocks
import ftl.environment.toCliTable

interface ListIPBlocks

operator fun ListIPBlocks.invoke() {
    // TODO move toCliTable() to presentation layer during refactor of presentation after #1728
    logLn(fetchIpBlocks().toCliTable())
}
