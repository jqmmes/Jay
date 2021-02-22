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

package pt.up.fc.dcc.hyrax.jay

import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.*
import pt.up.fc.dcc.hyrax.jay.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TaskExecutorManager
import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant
import java.io.File
import kotlin.io.path.ExperimentalPathApi

@ExperimentalPathApi
class Jay : AbstractJay() {

    private var brokerStarted = false
    private val LOCK = Object()
    private val fsAssistant = FileSystemAssistant()

    private fun startBroker() {
        synchronized(LOCK) {
            if (!brokerStarted) {
                startBroker(fsAssistant = fsAssistant)
                Thread.sleep(500)
            }
            brokerStarted = true
        }
    }

    override fun startWorker() {
        startBroker()
        WorkerService.start(TaskExecutorManager(fsAssistant))
    }

    override fun startProfiler(fsAssistant: pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant?) {
        startBroker()
        ProfilerService.start(powerMonitor = X86PowerMonitor,
                transportManager = X86TransportManager, cpuManager = X86CPUManager, usageManager = X86sageManager,
                sensorManager = X86SensorManager, recordingDir = File("profiler_recordings"))
    }
}