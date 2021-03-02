package pt.up.fc.dcc.hyrax.jay.structures

import pt.up.fc.dcc.hyrax.jay.proto.JayProto

data class CurrentEstimations(val idle: Float,
                              val compute: Float,
                              val rx: Float,
                              val tx: Float,
                              val batteryLevel: Int,
                              val batteryCapacity: Float,
                              val batteryVoltage: Float) {

    constructor(proto: JayProto.CurrentEstimations): this(
        proto.idle,
        proto.compute,
        proto.rx,
        proto.tx,
        proto.batteryLevel,
        proto.batteryCapacity,
        proto.batteryVoltage
    )

    internal fun getProto(): JayProto.CurrentEstimations? {
        return JayProto.CurrentEstimations.newBuilder()
            .setIdle(idle)
            .setCompute(compute)
            .setRx(rx)
            .setTx(tx)
            .setBatteryLevel(batteryLevel)
            .setBatteryCapacity(batteryCapacity)
            .setBatteryVoltage(batteryVoltage)
            .build()
    }
}
