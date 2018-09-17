package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.ODCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.interfaces.RemoteODCallback
import pt.up.fc.dcc.hyrax.odlib.tensorflow.DroidTensorFlow

class ODLib : AbstractODLib(DroidTensorFlow()) {

    override fun detectObjects(image: String) {
        localDetector.detectObjects("")
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun detectObjects(image: String, remoteODClient: RemoteODClient) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun asyncDetectObjects(image: String, callback: ODCallback) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun asyncDetectObjects(image: String, remoteODClient: RemoteODClient, callback: RemoteODCallback) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}