package pt.up.fc.dcc.hyrax.odlib.status

import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.status.battery.BatteryDetails

object StatusManager {
    private var batteryDetails: BatteryDetails = BatteryDetails()

    fun setCustomBatteryDetails(customBatteryDetails: BatteryDetails) {
        this.batteryDetails = customBatteryDetails
    }

    fun announceStatus() {
        // TODO: Announce device status as multicast
        MulticastAdvertiser.setAdvertiseData(ByteArray(0))
    }
}