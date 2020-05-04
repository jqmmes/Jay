package pt.up.fc.dcc.hyrax.jay.services.worker.status.usage

import android.annotation.SuppressLint
import android.app.usage.UsageStatsManager
import android.app.usage.UsageStatsManager.INTERVAL_DAILY
import android.content.Context
import java.util.*

class JayUsageStatsManager(val context: Context) {

    /**
     * This method should return all usage events during the last usagePeriod.
     *
     *
     */

    @SuppressLint("ServiceCast")
    fun getRecentUsageList(usagePeriod: Long): Set<PackageUsages> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val pkgUsagesSet = HashSet<PackageUsages>()
        val usageEvents = usageStatsManager.queryUsageStats(INTERVAL_DAILY, time - usagePeriod, time)
        usageEvents.forEach { usageStat ->
            val pkgInList = pkgUsagesSet.find { element -> PackageUsages(usageStat.packageName) == element }
            if (pkgInList == null) {
                val newPkg = PackageUsages(usageStat.packageName)
                newPkg.startTime =
                        if (usageStat.firstTimeStamp < time - usagePeriod) time - usagePeriod
                        else usageStat.firstTimeStamp
                newPkg.addUsageTime(newPkg.startTime - (time - usagePeriod), usageStat.lastTimeStamp - (time - usagePeriod))
            } else {
                pkgInList.startTime =
                        if ((usageStat.firstTimeStamp < time - usagePeriod)) time - usagePeriod
                        else if (usageStat.firstTimeStamp < pkgInList.startTime) usageStat.firstTimeStamp
                        else pkgInList.startTime
                pkgInList.addUsageTime(pkgInList.startTime - (time - usagePeriod), usageStat.lastTimeStamp - (time - usagePeriod))
            }
        }
        return pkgUsagesSet.toSet()
    }
}

