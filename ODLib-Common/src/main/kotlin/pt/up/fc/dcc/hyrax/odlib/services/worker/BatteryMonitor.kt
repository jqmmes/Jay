package pt.up.fc.dcc.hyrax.odlib.services.worker

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto

abstract class BatteryMonitor {
    abstract fun setCallbacks(levelChangeCallback: (Int, Int, Float) -> Unit, statusChangeCallback: (ODProto.Worker.BatteryStatus) -> Unit)
    abstract fun monitor()
    abstract fun destroy()
    abstract fun getBatteryCurrentNow(): Int
    abstract fun getBatteryRemainingEnergy(): Long
    abstract fun getBatteryCharge(): Int
}