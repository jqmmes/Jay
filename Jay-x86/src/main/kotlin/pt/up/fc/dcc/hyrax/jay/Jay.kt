/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay

import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.*
import pt.up.fc.dcc.hyrax.jay.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TaskExecutorManager
import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant
import java.io.File

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