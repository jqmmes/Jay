/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage

import pt.up.fc.dcc.hyrax.jay.structures.TimeFrame


@Suppress("unused")
class PackageUsages {

    constructor()

    constructor(pkgName: String) {
        this.pkgName = pkgName
    }

    lateinit var pkgName: String
    private val usageTimes = LinkedHashSet<TimeFrame>()
    var startTime: Long = 0


    fun addUsageTime(begin: Long, end: Long) {
        if (end < begin) throw (Error("Usage Time can't have end < begin"))
        usageTimes.forEach { timeFrame ->
            if ((begin >= timeFrame.start && begin <= timeFrame.end) && (end > timeFrame.end)) {
                usageTimes.remove(timeFrame)
                usageTimes.add(TimeFrame(timeFrame.start, end))
                return
            }
            if ((timeFrame.start in (begin + 1)..end)) {
                usageTimes.remove(timeFrame)
                usageTimes.add(TimeFrame(timeFrame.start, end))
                return
            }
        }
        usageTimes.add(TimeFrame(begin, end))
    }

    fun getUsageTimes(): Set<TimeFrame> {
        return usageTimes.toSet()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null)
            return false
        if (other.javaClass.isInstance(PackageUsages::class))
            return (other as PackageUsages).pkgName == pkgName
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return pkgName.hashCode()
    }
}