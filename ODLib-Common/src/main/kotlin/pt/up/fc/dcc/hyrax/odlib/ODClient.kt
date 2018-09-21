package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.AbstractODLib

open class ODClient {


    open fun configureModel() {

    }

    open fun detectObjects(imgPath: String) : List<ODUtils.ODDetection?>{
        return ODService.putJobAndWait(imgPath)
    }

    open fun asyncDetectObjects(imgPath: String, callback: (List<ODUtils.ODDetection?>) -> Unit) {
        ODService.putJob(imgPath, callback)
    }

    open fun getPort() : Int{
        return AbstractODLib.getServerPort()
    }

    open fun getAdress() : String {
        return "localhost"
    }
}