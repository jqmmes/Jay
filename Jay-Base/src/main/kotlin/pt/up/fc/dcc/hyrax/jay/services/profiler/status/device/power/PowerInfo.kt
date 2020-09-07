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