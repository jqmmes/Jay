/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
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

