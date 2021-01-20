/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerStatus
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power.PowerMonitor
import java.io.File

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
                val value = powerFile.readText().toFloat()
                return value.unaryMinus()
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

    override fun getFixedPowerEstimations(): JayProto.PowerEstimations {
        return JayProto.PowerEstimations.newBuilder()
                .setBatteryCapacity(getCapacity())
                .setBatteryLevel(100)
                .setCompute(94.24f)
                .setIdle(34.7f)
                .setRx(35.61f)
                .setTx(25.61f)
                .build()
    }

}
