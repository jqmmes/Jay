/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage

import android.app.usage.UsageStatsManager
import android.app.usage.UsageStatsManager.INTERVAL_BEST
import android.content.Context
import java.util.*

object AndroidUsageManager : UsageManager {


    private lateinit var context: Context

    fun setContext(context: Context) {
        this.context = context
    }

    /**
     * This method should return all usage events during the last usagePeriod.
     *
     * The usage period can be calculated by an interval of time between a startRecording and a stopRecording
     * if we ask for a instant usage, retrieve last N seconds
     */
    override fun getRecentUsageList(usagePeriod: Long): Set<PackageUsages> {
        if (!this::context.isInitialized) throw AssertionError("Must setContext before getTransport")
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val pkgUsagesSet = HashSet<PackageUsages>()
        val usageEvents = usageStatsManager.queryUsageStats(INTERVAL_BEST, time - usagePeriod, time)
        usageEvents.forEach { usageStat ->
            val pkgInList = pkgUsagesSet.find { element -> PackageUsages(usageStat.packageName) == element }
            if (pkgInList == null) {
                val newPkg = PackageUsages(usageStat.packageName)
                newPkg.startTime = when {
                    usageStat.firstTimeStamp < time - usagePeriod -> time - usagePeriod
                    else -> usageStat.firstTimeStamp
                }
                //if (usageStat.lastTimeStamp >= (time - usagePeriod))
                //newPkg.addUsageTime(newPkg.startTime - (time - usagePeriod), usageStat.lastTimeStamp - (time - usagePeriod))
                newPkg.addUsageTime((time - usagePeriod), time)
                pkgUsagesSet.add(newPkg)
            } else {
                pkgInList.startTime =
                        when {
                            usageStat.firstTimeStamp < time - usagePeriod -> time - usagePeriod
                            usageStat.firstTimeStamp < pkgInList.startTime -> usageStat.firstTimeStamp
                            else -> pkgInList.startTime
                        }
                //if (usageStat.lastTimeStamp >= (time - usagePeriod))
                //    pkgInList.addUsageTime(pkgInList.startTime - (time - usagePeriod), usageStat.lastTimeStamp - (time - usagePeriod))
                pkgInList.addUsageTime((time - usagePeriod), time)

            }
        }
        return pkgUsagesSet.toSet()
    }

    override fun getTotalMemory(): Long {
        return Runtime.getRuntime().totalMemory()
    }

    override fun getFreeMemory(): Long {
        return Runtime.getRuntime().freeMemory()
    }
}

