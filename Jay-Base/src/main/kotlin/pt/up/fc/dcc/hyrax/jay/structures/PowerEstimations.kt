package pt.up.fc.dcc.hyrax.jay.structures

import pt.up.fc.dcc.hyrax.jay.proto.JayProto

data class PowerEstimations(val idle: Float,
                            val compute: Float,
                            val rx: Float,
                            val tx: Float,
                            val batteryLevel: Int,
                            val batteryCapacity: Float
                            ) {

    constructor(proto: JayProto.PowerEstimations): this(
        proto.idle,
        proto.compute,
        proto.rx,
        proto.tx,
        proto.batteryLevel,
        proto.batteryCapacity
    )

    internal fun getProto(): JayProto.PowerEstimations? {
        return JayProto.PowerEstimations.newBuilder()
            .setIdle(idle)
            .setCompute(compute)
            .setRx(rx)
            .setTx(tx)
            .setBatteryLevel(batteryLevel)
            .setBatteryCapacity(batteryCapacity)
            .build()
    }
}
