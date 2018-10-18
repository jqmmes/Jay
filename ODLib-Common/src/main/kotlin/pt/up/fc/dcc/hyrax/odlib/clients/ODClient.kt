package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob

open class ODClient {

    open var id : Long = 0

    open fun configureModel() {

    }

    open fun detectObjects(odJob: ODJob) : List<ODUtils.ODDetection?>{
        return ODComputingService.putJobAndWait(odJob)
    }

    open fun asyncDetectObjects(odJob: ODJob, callback: (List<ODUtils.ODDetection?>) -> Unit) {
        ODComputingService.putJob(odJob.getData(), callback)
    }

    open fun getPort() : Int{
        return ODSettings.serverPort
    }

    open fun getAddress() : String {
        return "localhost"
    }
}