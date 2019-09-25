package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.tensorflow.CloudletTensorFlow
import pt.up.fc.dcc.hyrax.odlib.utils.FileSystemAssistant

class ODLib : AbstractODLib() {

    override fun startWorker() {
        val fsAssistant = FileSystemAssistant()
        startBroker(fsAssistant = fsAssistant)
        WorkerService.start(localDetect = CloudletTensorFlow(), fsAssistant = fsAssistant)
    }
}