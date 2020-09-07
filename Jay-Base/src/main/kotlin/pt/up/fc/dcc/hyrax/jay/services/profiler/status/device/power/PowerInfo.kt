package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power

import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerStatus
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerStatus.UNKNOWN

class PowerInfo {

    constructor()

    constructor(level: Int, current: Int, power: Float,
                voltage: Float, temperature: Float,
                energy: Long, charge: Int,
                capacity: Int, status: PowerStatus) {
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
    var current: Int = -1
    var power: Float = -1f
    var voltage: Float = -1f
    var temperature: Float = -1f
    var energy: Long = -1L
    var charge: Int = -1
    var capacity: Int = -1
    var status: PowerStatus = UNKNOWN
}