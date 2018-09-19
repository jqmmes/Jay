package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.ODCallback

open class ODClient {


    //private lateinit var localDetector: DetectObjects


    open fun configureModel() {


    }

    open fun detectObjects(imgPath: String) : List<ODUtils.ODDetection?>{
        return ODService.putJobAndWait(imgPath)
    }

    open fun asyncDetectObjects(imgPath: String, callback: ODCallback) {
        ODService.putJob(imgPath, callback)
    }
}