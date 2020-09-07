package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device

import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage.PackageUsages
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage.UsageManager

object X86sageManager : UsageManager {
    override fun getRecentUsageList(usagePeriod: Long): Set<PackageUsages> {
        return emptySet()
    }

    override fun getTotalMemory(): Long {
        return Runtime.getRuntime().totalMemory()
    }

    override fun getFreeMemory(): Long {
        return Runtime.getRuntime().freeMemory()
    }

}
