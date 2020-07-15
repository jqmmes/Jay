package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.cpu

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import java.io.File
import java.util.*

object AndroidCPUManager : CPUManager() {

    /**
     * @param cpu_with_frequency
     * if set, this function returns only cpus with scaling data available
     *
     * @return cpu_set
     * returns a set of cpus in the device
     *
     * ----------------------------------------------------------
     *
     * Android Folders:
     *
     * /sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state
     *
     * ----------------------------------------------------------
     *
     * /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq
     * /sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq
     * /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq
     *
     * ----------------------------------------------------------
     *
     * /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq
     * /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq
     *
     */
    override fun getCpus(cpu_with_frequency: Boolean): Set<Int> {
        val rootCpuDir = File("/sys/devices/system/cpu/")
        val cpus: LinkedHashSet<Int> = LinkedHashSet()
        if (rootCpuDir.isDirectory) {
            rootCpuDir.listFiles { _, s -> s.startsWith("cpu", ignoreCase = true) }?.forEach { cpuDir ->
                if (File("${rootCpuDir.path}/${cpuDir.name}/cpufreq/scaling_cur_freq").exists() || !cpu_with_frequency) {
                    cpus.add(cpuDir.name.removePrefix("cpu").toInt())
                }
            }
        }
        return cpus
    }

    override fun getCurrentCPUClockSpeed(cpuNumber: Int): Long {
        val cpuFile = File("/sys/devices/system/cpu/cpu$cpuNumber/cpufreq/scaling_cur_freq")
        return try {
            if (cpuFile.exists()) {
                val scanner = Scanner(cpuFile)
                if (scanner.hasNextInt()) scanner.nextLong() else -1
            } else -1
        } catch (ignore: Exception) {
            JayLogger.logWarn("PROBLEM_READING_CPU_CLOCK_SPEED")
            -1
        }
    }
}