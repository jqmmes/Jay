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

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power

import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerEstimations
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerStatus

interface PowerMonitor {
    fun setCallbacks(_levelChangeCallback: (Int, Float, Float) -> Unit, _statusChangeCallback: (PowerStatus) -> Unit)
    fun monitor()
    fun destroy()
    fun getCurrentNow(): Float // Ampere (A)
    fun getPower(): Float // Watt (W)
    fun getRemainingEnergy(): Long // Watt-Hour (Wh)
    fun getCharge(): Float // Ampere-Hour (Ah)
    fun getCapacity(): Float // Ampere-Hour (Ah)
    fun getLevel(): Int
    fun getStatus(): PowerStatus
    fun getFixedPowerEstimations(): PowerEstimations
}