package pt.up.fc.dcc.hyrax.jay

import pt.up.fc.dcc.hyrax.jay.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TaskExecutorManager
import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant

/**
 * todo: registerTaskExecutor
 */
class Jay : AbstractJay() {

    override fun startWorker() {
        val fsAssistant = FileSystemAssistant()
        startBroker(fsAssistant = fsAssistant)
        WorkerService.start(TaskExecutorManager(fsAssistant))
    }
}