package pt.up.fc.dcc.hyrax.odlib.status

import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.status.battery.BatteryDetails
import pt.up.fc.dcc.hyrax.odlib.status.cpu.CpuDetails

object StatusManager {
    private var batteryDetails: BatteryDetails = BatteryDetails()
    var cpuDetails = CpuDetails

    fun setCustomBatteryDetails(customBatteryDetails: BatteryDetails) {
        this.batteryDetails = customBatteryDetails
    }

    fun announceStatus() {
        // TODO: Announce device status as multicast
        MulticastAdvertiser.setAdvertiseData(ByteArray(0))
    }
}