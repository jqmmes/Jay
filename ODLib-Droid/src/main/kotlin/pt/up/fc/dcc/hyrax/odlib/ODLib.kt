package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.tensorflow.DroidTensorFlow
import android.content.Context
import pt.up.fc.dcc.hyrax.odlib.status.battery.DroidBatteryDetails

class ODLib(context : Context) : AbstractODLib(DroidTensorFlow(context)) {

    init {
        DroidBatteryDetails.monitorBattery(context)
    }

    /*override fun getDetector() : DroidTensorFlow {
        return (localDetector as DroidTensorFlow)
    }*/
}