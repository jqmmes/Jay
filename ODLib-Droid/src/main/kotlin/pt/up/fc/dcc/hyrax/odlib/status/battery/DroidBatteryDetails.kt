package pt.up.fc.dcc.hyrax.odlib.status.battery

import pt.up.fc.dcc.hyrax.odlib.enums.BatteryStatus

// TODO: Monitor Android Device Battery Status
object DroidBatteryDetails : BatteryDetails() {

    fun monitorBattery() {
        setPercentage(10)
        setStatus(BatteryStatus.DISCHARGING)
        setCapacity(3000)
        setRemainCapacity(2000)
    }
}