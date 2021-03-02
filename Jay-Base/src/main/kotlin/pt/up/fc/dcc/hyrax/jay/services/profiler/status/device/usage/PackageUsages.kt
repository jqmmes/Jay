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

import pt.up.fc.dcc.hyrax.jay.structures.TimeFrame



class PackageUsages(val pkgName: String) {

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