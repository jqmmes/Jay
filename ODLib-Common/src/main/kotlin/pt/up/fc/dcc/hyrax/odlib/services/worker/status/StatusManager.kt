/*
package pt.up.fc.dcc.hyrax.odlib.services.worker.status

import pt.up.fc.dcc.hyrax.odlib.enums.BatteryStatus
import pt.up.fc.dcc.hyrax.odlib.services.broker.multicast.MulticastAdvertiser
//import pt.up.fc.dcc.hyrax.odlib.services.worker.status.cpu.CpuDetails
import pt.up.fc.dcc.hyrax.odlib.utils.DeviceInformation
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

object StatusManager {
    private val deviceInformation = DeviceInformation()
    //var cpuDetails = CpuDetails

    fun advertiseStatus() {
        //MulticastAdvertiser.setAdvertiseData(1, ODUtils.genDeviceStatus(deviceInformation).toByteArray())
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
        deviceInformation.pendingJobs = idle
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

    fun setBatteryPercentage(percentage: Int) {
        deviceInformation.battery = percentage
        updateAnnounceStatus()
    }

    fun setBatteryStatus(status: BatteryStatus) {
        deviceInformation.batteryStatus = status
        updateAnnounceStatus()
    }
}*/
