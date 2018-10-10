package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.ODComputingService
import pt.up.fc.dcc.hyrax.odlib.ODSettings
import pt.up.fc.dcc.hyrax.odlib.ODUtils
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob

open class ODClient {

    open var id : Long = 0

    open fun configureModel() {

    }

    open fun detectObjects(imgPath: String) : List<ODUtils.ODDetection?>{
        return ODComputingService.putJobAndWait(imgPath)
    }

    open fun asyncDetectObjects(job: ODJob, callback: (List<ODUtils.ODDetection?>) -> Unit) {
        ODComputingService.putJob(job.getData(), callback)
    }

    open fun asyncDetectObjects(imgPath: String, callback: (List<ODUtils.ODDetection?>) -> Unit) {
        ODComputingService.putJob(imgPath, callback)
    }

    open fun getPort() : Int{
        return ODSettings.serverPort
    }

    open fun getAddress() : String {
        return "localhost"
    }
}