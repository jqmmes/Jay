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

import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerStatus
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerStatus.UNKNOWN

class PowerInfo {

    constructor()

    constructor(level: Int, current: Float, power: Float,
                voltage: Float, temperature: Float,
                energy: Long, charge: Float,
                capacity: Float, status: PowerStatus) {
        this.level = level
        this.current = current
        this.power = power
        this.voltage = voltage
        this.temperature = temperature
        this.energy = energy
        this.charge = charge
        this.capacity = capacity
        this.status = status
    }

    var level: Int = -1
    var current: Float = -1f
    var power: Float = -1f
    var voltage: Float = -1f
    var temperature: Float = -1f
    var energy: Long = -1L
    var charge: Float = -1f
    var capacity: Float = -1f
    var status: PowerStatus = UNKNOWN
}