package pt.up.fc.dcc.hyrax.odlib.enums

enum class BatteryStatus(value: Int) {
    CHARGED(0),
    CHARGING(1),
    USB(2),
    DISCHARGING(3)
}