package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.enums.BatteryStatus
import pt.up.fc.dcc.hyrax.odlib.status.network.LatencyMovingAverage

class DeviceInformation {
    var battery: Int = 100
    var batteryStatus: BatteryStatus = BatteryStatus.CHARGED
    var deviceTemperature: Float = 0.0f
    var connectionQuality: Float = 1.0f
    var computationThreads: Int = 0
    var runningJobs: Int = 0
    var pendingJobs: Int = 0
    var availableDiskSpace: Long = 0L
    var networkLatency = LatencyMovingAverage()
    var rtt: Float = 0f
    var queueSize: Int = 0
    var connections: Int = 0
}