package ftl.client.google

import com.google.testing.model.Date
import com.google.testing.model.DeviceIpBlock
import ftl.gc.GcTesting
import ftl.http.executeWithRetry
import ftl.reports.api.twoDigitString

internal fun deviceIPBlocks(): List<DeviceIpBlock> = GcTesting.get.testEnvironmentCatalog()
    .get("DEVICE_IP_BLOCKS")
    .executeWithRetry()
    ?.deviceIpBlockCatalog
    ?.ipBlocks
    .orEmpty()

// yyyy-mm-dd
internal fun Date.prettyDate() =
    if (listOf(year, month, day).any { it == null }) null
    else "$year-${month.twoDigitString()}-${day.twoDigitString()}"
