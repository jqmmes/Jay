package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.enums.BatteryStatus

class DeviceInformation {
    var battery: Int = 0
    var batteryStatus: BatteryStatus = BatteryStatus.DISCHARGING
    var deviceTemperature: Float = 0.0f
    var connectionQuality: Float = 1.0f
    var computationThreads: Int = 0
    var runningJobs: Int = 0
    var pendingJobs: Int = 0
    var availableDiskSpace: Long = 0L
    var rtt: Float = 0f
}