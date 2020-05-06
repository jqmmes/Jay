package pt.up.fc.dcc.hyrax.jay.services.worker.status.device.cpu

object AndroidCPUManager : CPUManager() {
    override fun getCpus() {
        // Android
        "/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state"

        "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq"
        "/sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq"
        "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq"

        "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq"
        "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"
    }

    override fun getCurrentCPUClockSpeed(cpuNumber: Int) {

    }
}