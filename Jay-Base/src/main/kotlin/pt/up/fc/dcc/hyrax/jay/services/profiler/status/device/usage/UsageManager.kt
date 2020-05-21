package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage

interface UsageManager {
    fun getRecentUsageList(usagePeriod: Long): Set<PackageUsages>
}