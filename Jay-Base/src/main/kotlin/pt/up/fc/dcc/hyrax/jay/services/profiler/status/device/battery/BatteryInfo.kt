package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery

import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Worker.BatteryStatus

@Suppress("unused")
class BatteryInfo {

    constructor()

    constructor(batteryLevel: Int, batteryCurrent: Int,
                batteryVoltage: Int, batteryTemperature: Float,
                batteryEnergy: Long, batteryCharge: Int,
                batteryStatus: BatteryStatus) {
        this.batteryLevel = batteryLevel
        this.batteryCurrent = batteryCurrent
        this.batteryVoltage = batteryVoltage
        this.batteryTemperature = batteryTemperature
        this.batteryEnergy = batteryEnergy
        this.batteryCharge = batteryCharge
        this.batteryStatus = batteryStatus
    }

    var batteryLevel: Int = -1
    var batteryCurrent: Int = -1
    var batteryVoltage: Int = -1
    var batteryTemperature: Float = -1f
    var batteryEnergy: Long = -1L
    var batteryCharge: Int = -1
    var batteryStatus: BatteryStatus = BatteryStatus.UNRECOGNIZED
}