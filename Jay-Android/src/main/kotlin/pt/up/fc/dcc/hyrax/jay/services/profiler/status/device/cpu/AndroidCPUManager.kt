package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.cpu

import android.content.Context
import java.io.File

object AndroidCPUManager : CPUManager() {

    private lateinit var context: Context

    fun setContext(context: Context) {
        this.context = context
    }

    override fun getCpus(cpu_with_frequency: Boolean): Set<Int> {
        // Android
        /*"/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state"

        "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq"
        "/sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq"
        "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"

        "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq"
        "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"*/
        File("/sys/devices/system/cpu/")
        return emptySet()
    }

    override fun getCurrentCPUClockSpeed(cpuNumber: Int): Int {
        return 0
    }
}