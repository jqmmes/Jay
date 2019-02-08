package pt.up.fc.dcc.hyrax.odlib.utils

import pt.up.fc.dcc.hyrax.odlib.enums.BatteryStatus

@Deprecated("Will be integrated in Worker")
class DeviceInformation (var battery: Int = 100,
                         var batteryStatus: BatteryStatus = BatteryStatus.CHARGED,
                         var deviceTemperature: Float = 0.0f,
                         var connectionQuality: Float = 1.0f,
                         var computationThreads: Int = 0,
                         var runningJobs: Int = 0,
                         var pendingJobs: Int = 0,
                         var availableDiskSpace: Long = 0L,
                         var networkLatency: LatencyMovingAverage = LatencyMovingAverage(),
                         var rtt: Float = 0f,
                         var queueSize: Int = 0,
                         var connections: Int = 0) {

    override fun equals(other: Any?): Boolean {
        other as DeviceInformation
        if (this.battery!=other.battery) return false
        if (this.batteryStatus!=other.batteryStatus) return false
        if (this.deviceTemperature!=other.deviceTemperature) return false
        if (this.connectionQuality!=other.connectionQuality) return false
        if (this.computationThreads!=other.computationThreads) return false
        if (this.runningJobs!=other.runningJobs) return false
        if (this.pendingJobs!=other.pendingJobs) return false
        if (this.availableDiskSpace!=other.availableDiskSpace) return false
        if (this.networkLatency.getAvgLatency()!=other.networkLatency.getAvgLatency()) return false
        if (this.rtt!=other.rtt) return false
        if (this.queueSize!=other.queueSize) return false
        if (this.connections!=other.connections) return false
        return true
    }

    override fun hashCode(): Int {
        var result = battery
        result = 31 * result + batteryStatus.hashCode()
        result = 31 * result + deviceTemperature.hashCode()
        result = 31 * result + connectionQuality.hashCode()
        result = 31 * result + computationThreads
        result = 31 * result + runningJobs
        result = 31 * result + pendingJobs
        result = 31 * result + availableDiskSpace.hashCode()
        result = 31 * result + networkLatency.hashCode()
        result = 31 * result + rtt.hashCode()
        result = 31 * result + queueSize
        result = 31 * result + connections
        return result
    }


}