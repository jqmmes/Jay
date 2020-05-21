package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery

import pt.up.fc.dcc.hyrax.jay.proto.JayProto


// Todo: Adapt batteryMonitor to the new format
interface BatteryMonitor {
    fun setCallbacks(levelChangeCallback: (Int, Int, Float) -> Unit, statusChangeCallback: (JayProto.Worker.BatteryStatus) -> Unit)
    fun monitor()
    fun destroy()
    fun getBatteryCurrentNow(): Int
    fun getBatteryRemainingEnergy(): Long
    fun getBatteryCharge(): Int
}