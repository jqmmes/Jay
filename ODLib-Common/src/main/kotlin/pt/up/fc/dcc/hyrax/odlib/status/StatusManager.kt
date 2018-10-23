package pt.up.fc.dcc.hyrax.odlib.status

import pt.up.fc.dcc.hyrax.odlib.clients.DeviceInformation
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.status.battery.BatteryDetails
import pt.up.fc.dcc.hyrax.odlib.status.cpu.CpuDetails
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

object StatusManager {
    private val deviceInformation = DeviceInformation()
    private var batteryDetails: BatteryDetails = BatteryDetails()
    var cpuDetails = CpuDetails

    fun setCustomBatteryDetails(customBatteryDetails: BatteryDetails) {
        this.batteryDetails = customBatteryDetails
    }

    fun advertiseStatus() {
        MulticastAdvertiser.setAdvertiseData(1, ODUtils.genDeviceStatus(deviceInformation).toByteArray())
    }

    private fun updateAnnounceStatus() {
        if (MulticastAdvertiser.getCurrentAdvertiseType() == 1) advertiseStatus()
    }

    fun getDeviceInformation() : DeviceInformation {
        return deviceInformation
    }

    fun setCpuWorkers(workers: Int) {
        deviceInformation.computationThreads = workers
        updateAnnounceStatus()
    }

    fun setRunningJobs(running: Int) {
        deviceInformation.runningJobs = running
        updateAnnounceStatus()
    }

    fun setIdleJobs(idle: Int) {
        deviceInformation.pendingJobs
        updateAnnounceStatus()
    }

    fun setQueueSize(queueSize: Int) {
        deviceInformation.queueSize = queueSize
        updateAnnounceStatus()
    }

    fun setConnections(connections: Int) {
        deviceInformation.connections = connections
        updateAnnounceStatus()
    }
}