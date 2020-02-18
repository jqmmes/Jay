package pt.up.fc.dcc.hyrax.jay

import pt.up.fc.dcc.hyrax.jay.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TensorflowTaskExecutor
import pt.up.fc.dcc.hyrax.jay.structures.Detection
import pt.up.fc.dcc.hyrax.jay.tensorflow.CloudletTensorFlow
import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant

class Jay : AbstractJay() {

    override fun startWorker() {
        val fsAssistant = FileSystemAssistant()
        startBroker(fsAssistant = fsAssistant)
        val taskExecutor = TensorflowTaskExecutor<List<Detection>>("TensorflowWorker")
        taskExecutor.init(CloudletTensorFlow())
        WorkerService.start(taskExecutor, localDetect = CloudletTensorFlow(), fsAssistant = fsAssistant)
    }
}