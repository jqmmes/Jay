package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerStatus
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power.PowerMonitor
import java.io.File
import java.util.*

object X86PowerMonitor : PowerMonitor {
    override fun setCallbacks(_levelChangeCallback: (Int, Float, Float) -> Unit, _statusChangeCallback: (PowerStatus) -> Unit) {}
    override fun monitor() {}

    override fun destroy() {
        JayLogger.logInfo("CLOSING", "", "POWER_MONITOR")
    }

    override fun getCurrentNow(): Float {
        return 0f
    }

    /**
     * Returns the instant power spent on cloudlet in Watt.
     *
     * We need to negate this value to match battery usage, which creates a negative current.
     */
    override fun getPower(): Float {
        try {
            val powerFile = File("/tmp/instant_consumption.power")
            if (powerFile.exists() && powerFile.isFile) {
                return Scanner(powerFile).nextFloat().unaryMinus()
            }
        } catch (ignore: Exception) {
        }
        return 0f
    }

    override fun getRemainingEnergy(): Long {
        return Long.MAX_VALUE
    }

    override fun getCharge(): Float {
        return Float.MAX_VALUE
    }

    override fun getCapacity(): Float {
        return Float.MAX_VALUE
    }

    override fun getLevel(): Int {
        return 100
    }

    override fun getStatus(): PowerStatus {
        return PowerStatus.FULL
    }

}
