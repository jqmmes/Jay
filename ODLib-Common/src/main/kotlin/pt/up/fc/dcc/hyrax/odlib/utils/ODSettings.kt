package pt.up.fc.dcc.hyrax.odlib.utils

object ODSettings {
    var serverPort : Int = 50051
    const val rttPort: Int = 8888
    const val cloudIp: String = "35.204.130.183"
    const val grpcTimeout: Long = 10 //Seconds
    const val grpcLongTimeout: Long = 120 //Seconds
    const val grpcShortTimeout: Long = 500 //Milliseconds
    const val grpcMaxMessageSize: Int = 150000000
}