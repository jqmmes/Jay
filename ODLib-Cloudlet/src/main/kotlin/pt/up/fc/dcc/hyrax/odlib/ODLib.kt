package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.tensorflow.CloudletTensorFlow

class ODLib : AbstractODLib() {

    override fun startWorker() {
        startBroker()
        WorkerService.start(CloudletTensorFlow())
    }
}