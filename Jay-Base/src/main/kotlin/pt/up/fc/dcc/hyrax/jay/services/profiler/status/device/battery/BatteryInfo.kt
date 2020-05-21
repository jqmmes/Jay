package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery

import pt.up.fc.dcc.hyrax.jay.proto.JayProto

data class BatteryInfo(
        val batteryLevel: Int,
        val batteryCurrent: Long,
        val batteryVoltage: Int,
        val batteryTemperature: Int,
        val batteryEnergy: Long,
        val batteryCharge: Int,
        val batteryStatus: JayProto.Worker.BatteryStatus
)