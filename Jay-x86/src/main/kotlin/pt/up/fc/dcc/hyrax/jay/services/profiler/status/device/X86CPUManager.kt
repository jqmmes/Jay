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

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.cpu.CPUManager
import java.io.File
import java.util.*

object X86CPUManager : CPUManager() {
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
