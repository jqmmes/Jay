package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.tensorflow.DroidTensorFlow
import android.content.Context

class ODLib(context : Context) : AbstractODLib(DroidTensorFlow(context)) {

    override fun getDetector() : DroidTensorFlow {
        return (localDetector as DroidTensorFlow)
    }

    companion object {
        fun droidLog(message : String) {
            AbstractODLib.log(message)
        }
    }
}