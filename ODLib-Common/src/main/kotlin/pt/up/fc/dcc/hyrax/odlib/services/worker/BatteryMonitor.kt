package pt.up.fc.dcc.hyrax.odlib.services.worker

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto

abstract class BatteryMonitor {

    abstract fun setCallbacks(levelChangeCallback: (Int) -> Unit, statusChangeCallback: (ODProto.Worker.BatteryStatus) -> Unit)
    abstract fun monitor()
    abstract fun destroy()

}