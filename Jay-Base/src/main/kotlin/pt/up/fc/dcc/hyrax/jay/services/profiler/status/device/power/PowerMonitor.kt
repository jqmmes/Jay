package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power

import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerStatus


interface PowerMonitor {
    fun setCallbacks(_levelChangeCallback: (Int, Float, Float) -> Unit, _statusChangeCallback: (PowerStatus) -> Unit)
    fun monitor()
    fun destroy()
    fun getCurrentNow(): Int
    fun getPower(): Float
    fun getRemainingEnergy(): Long
    fun getCharge(): Int
    fun getCapacity(): Int
    fun getLevel(): Int
    fun getStatus(): PowerStatus
}