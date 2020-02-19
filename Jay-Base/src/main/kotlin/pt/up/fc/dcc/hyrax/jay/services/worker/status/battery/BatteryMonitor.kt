package pt.up.fc.dcc.hyrax.jay.services.worker.status.battery

import pt.up.fc.dcc.hyrax.jay.proto.JayProto

abstract class BatteryMonitor {
    abstract fun setCallbacks(levelChangeCallback: (Int, Int, Float) -> Unit, statusChangeCallback: (JayProto.Worker.BatteryStatus) -> Unit)
    abstract fun monitor()
    abstract fun destroy()
    abstract fun getBatteryCurrentNow(): Int
    abstract fun getBatteryRemainingEnergy(): Long
    abstract fun getBatteryCharge(): Int
}