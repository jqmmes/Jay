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

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.cpu

import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

abstract class CPUManager {

    private var cpuReadsInterval = 500L // time in ms
    private val recordingFlag: AtomicBoolean = AtomicBoolean(false)
    private val recordedStats: MutableSet<CPUStat> = LinkedHashSet()

    /**
     * To List available CPU's read the /sys/devices/system/cpu/cpuXX
     * To read available information about current cpufreq, check which /sys/devices/system/cpu/cpuXX
     * have cpufreq, then check scaling_cur_freq to see current cpu frequency, and cpuinfo_min_freq/
     * cpuinfo_max_freq to check max and min cpu frequencies available.
     *
     * If all flag is set return all cpus, else return only cpus that report frequency values
     *
     * On Google cloud read:
     * /proc/cpuinfo
     *
     */
    abstract fun getCpus(cpu_with_frequency: Boolean = true): Set<Int>

    abstract fun getCurrentCPUClockSpeed(cpuNumber: Int): Long

    fun getRecordableCPUCount(): Int {
        return getCpus(true).size
    }

    fun startCPUStatsRecordings() {
        recordCPUStats()
    }

    fun stopCPUStatsRecordings() {
        recordingFlag.set(false)
    }

    fun clearStats() {
        recordedStats.clear()
    }

    fun getCPURecordings(): Set<CPUStat> {
        return recordedStats
    }

    private fun recordCPUStats(): Boolean {
        if (!recordingFlag.compareAndSet(false, true)) return false
        Thread {
            do {
                getCpus()
                recordedStats.add(CPUStat(System.currentTimeMillis(), setOf(1, 2)))
                sleep(cpuReadsInterval)
            } while (recordingFlag.get())
        }
        return true
    }
}

