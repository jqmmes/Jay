package pt.up.fc.dcc.hyrax.jay

import pt.up.fc.dcc.hyrax.jay.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TaskExecutorManager
import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant

class Jay : AbstractJay() {

    override fun startWorker() {
        //val fsAssistant = FileSystemAssistant()
        /*startBroker(fsAssistant = fsAssistant)
        val taskExecutor = TensorflowTaskExecutor("TensorflowWorker")
        taskExecutor.init(CloudletTensorFlow())*/

        val taskExecutorManager = TaskExecutorManager(FileSystemAssistant())

        //WorkerService.start(taskExecutorManager, taskExecutor, localDetect = CloudletTensorFlow(), fsAssistant = fsAssistant)
        WorkerService.start(taskExecutorManager)
    }
}