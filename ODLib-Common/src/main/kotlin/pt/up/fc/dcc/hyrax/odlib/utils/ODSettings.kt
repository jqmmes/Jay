package pt.up.fc.dcc.hyrax.odlib.utils

object ODSettings {


    var brokerPort : Int = 50051
    val workerPort : Int = 50053
    val schedulerPort : Int = 50055
    val profilerPort : Int = 50057


    const val rttPort: Int = 8888
    const val cloudIp: String = "35.204.130.183"
    const val grpcTimeout: Long = 10 //Seconds
    const val grpcLongTimeout: Long = 120 //Seconds
    const val grpcShortTimeout: Long = 500 //Milliseconds
    const val grpcMaxMessageSize: Int = 150000000
}