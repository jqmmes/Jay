package pt.up.fc.dcc.hyrax.jay.services.worker.status.cpu

import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

object CpuStatsMonitor {

    private var cpuReadsInterval = 500L // time in ms
    private val recordingFlag: AtomicBoolean = AtomicBoolean(false)
    private val recordedStats: MutableSet<CPUStat> = LinkedHashSet<CPUStat>()

    /**
     * To List available CPU's read the /sys/devices/system/cpu/cpuXX
     * To read available information about current cpufreq, check which /sys/devices/system/cpu/cpuXX
     * have cpufreq, then check scaling_cur_freq to see current cpu frequency, and cpuinfo_min_freq/
     * cpuinfo_max_freq to check max and min cpu frequencies available.
     *
     */
    fun listCpus() {
        "/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state"

        "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq"
        "/sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq"
        "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"

        "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq"
        "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"
    }

    fun getRecordableCPUCount(): Int {


        return 1
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
                listCpus()
                recordedStats.add(CPUStat(System.currentTimeMillis(), setOf<Int>(1, 2)))
                sleep(cpuReadsInterval)
            } while (recordingFlag.get())
        }.start()
        return true
    }
}

