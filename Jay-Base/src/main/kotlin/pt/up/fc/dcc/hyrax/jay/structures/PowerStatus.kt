package pt.up.fc.dcc.hyrax.jay.structures

enum class PowerStatus {
    FULL, // Full
    AC_CHARGING, // AC Charging
    USB_CHARGING, // USB Charging
    QI_CHARGING, // Wireless Charging
    CHARGING, // Charging with unknown method
    DISCHARGING, // Unplugged
    UNKNOWN
}