package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery

import pt.up.fc.dcc.hyrax.jay.proto.JayProto.BatteryStatus
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.BatteryStatus.UNKNOWN

@Suppress("unused")
class BatteryInfo {

    constructor()

    constructor(batteryLevel: Int, batteryCurrent: Int,
                batteryVoltage: Int, batteryTemperature: Float,
                batteryEnergy: Long, batteryCharge: Int,
                batteryCapacity: Int, batteryStatus: BatteryStatus) {
        this.batteryLevel = batteryLevel
        this.batteryCurrent = batteryCurrent
        this.batteryVoltage = batteryVoltage
        this.batteryTemperature = batteryTemperature
        this.batteryEnergy = batteryEnergy
        this.batteryCharge = batteryCharge
        this.batteryCapacity = batteryCapacity
        this.batteryStatus = batteryStatus
    }

    var batteryLevel: Int = -1
    var batteryCurrent: Int = -1
    var batteryVoltage: Int = -1
    var batteryTemperature: Float = -1f
    var batteryEnergy: Long = -1L
    var batteryCharge: Int = -1
    var batteryCapacity: Int = -1
    var batteryStatus: BatteryStatus = UNKNOWN
}