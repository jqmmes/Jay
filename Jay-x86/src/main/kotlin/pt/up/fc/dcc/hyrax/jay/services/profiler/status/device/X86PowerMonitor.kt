/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
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
     * Returns the instant power spent on Cloudlet in Watt.
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
