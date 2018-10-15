package pt.up.fc.dcc.hyrax.odlib.status.battery

import pt.up.fc.dcc.hyrax.odlib.enums.BatteryStatus

open class BatteryDetails {
    companion object {
        private var batteryPercentage : Int = 100
        private var batteryStatus : BatteryStatus = BatteryStatus.CHARGED
        private var batteryCapacity : Int = Int.MAX_VALUE
        private var remainingCapacity : Int = Int.MAX_VALUE

        @JvmStatic
        protected fun setPercentage(batteryPercentage: Int) {
            this.batteryPercentage = batteryPercentage
        }

        @JvmStatic
        protected fun setStatus(batteryStatus: BatteryStatus) {
            this.batteryStatus = batteryStatus
        }

        @JvmStatic
        protected fun setCapacity(batteryCapacity : Int) {
            this.batteryCapacity = batteryCapacity
        }

        @JvmStatic
        protected fun setRemainCapacity(remainingCapacity: Int) {
            this.remainingCapacity = remainingCapacity
        }

        fun getPercentage() : Int {
            return batteryPercentage
        }

        fun getCapacity() : Int {
            return batteryCapacity
        }

        fun getRemainingCapacity() : Int {
            return remainingCapacity
        }

        fun getStatus() : BatteryStatus {
            return batteryStatus
        }
    }
}