package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery

import pt.up.fc.dcc.hyrax.jay.proto.JayProto.BatteryStatus


interface BatteryMonitor {
    fun setCallbacks(_levelChangeCallback: (Int, Int, Float) -> Unit, _statusChangeCallback: (BatteryStatus) -> Unit)
    fun monitor()
    fun destroy()
    fun getBatteryCurrentNow(): Int
    fun getBatteryRemainingEnergy(): Long
    fun getBatteryCharge(): Int
    fun getBatteryCapacity(): Int
    fun getBatteryLevel(): Int
    fun getBatteryStatus(): BatteryStatus

}