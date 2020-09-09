package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power

import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerEstimations
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerStatus

interface PowerMonitor {
    fun setCallbacks(_levelChangeCallback: (Int, Float, Float) -> Unit, _statusChangeCallback: (PowerStatus) -> Unit)
    fun monitor()
    fun destroy()
    fun getCurrentNow(): Float // Ampere (A)
    fun getPower(): Float // Watt (W)
    fun getRemainingEnergy(): Long // Watt-Hour (Wh)
    fun getCharge(): Float // Ampere-Hour (Ah)
    fun getCapacity(): Float // Ampere-Hour (Ah)
    fun getLevel(): Int
    fun getStatus(): PowerStatus
    fun getFixedPowerEstimations(): PowerEstimations
}