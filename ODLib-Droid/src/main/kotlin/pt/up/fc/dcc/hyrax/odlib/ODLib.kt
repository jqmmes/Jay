package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.ODLib
import pt.up.fc.dcc.hyrax.odlib.tensorflow.DroidTensorFlow
import android.content.Context

class ODLib(var context : Context) : ODLib(DroidTensorFlow(context)) {
    companion object {
        fun droidLog(message : String) {
            ODLib.log(message)
        }
    }
}