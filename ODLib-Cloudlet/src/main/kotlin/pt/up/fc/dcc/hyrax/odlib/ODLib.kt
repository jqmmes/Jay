package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.services.worker.workers.TensorflowWorker
import pt.up.fc.dcc.hyrax.odlib.structures.Detection
import pt.up.fc.dcc.hyrax.odlib.tensorflow.CloudletTensorFlow
import pt.up.fc.dcc.hyrax.odlib.utils.FileSystemAssistant

class ODLib : AbstractODLib() {

    override fun startWorker() {
        val fsAssistant = FileSystemAssistant()
        startBroker(fsAssistant = fsAssistant)
        val taskExecutor = TensorflowWorker<List<Detection>>("TensorflowWorker")
        taskExecutor.init(CloudletTensorFlow())
        WorkerService.start(taskExecutor, localDetect = CloudletTensorFlow(), fsAssistant = fsAssistant)
    }
}