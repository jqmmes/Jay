package pt.up.fc.dcc.hyrax.jay.structures

import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils

data class Power(val level: Int,
                 val current: Float,
                 val power: Float,
                 val voltage: Float,
                 val temperature: Float,
                 val energy: Long,
                 val charge: Float,
                 val capacity: Float,
                 val status: PowerStatus
                 ){

    internal fun getProto(): JayProto.Power {
        return JayProto.Power.newBuilder()
            .setLevel(level)
            .setCurrent(current)
            .setPower(power)
            .setVoltage(voltage)
            .setTemperature(temperature)
            .setEnergy(energy)
            .setCharge(charge)
            .setCapacity(capacity)
            .setStatus(JayUtils.powerStatusToProto(status))
            .build()
    }
}
