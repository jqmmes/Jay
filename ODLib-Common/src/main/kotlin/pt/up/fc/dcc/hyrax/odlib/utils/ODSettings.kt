package pt.up.fc.dcc.hyrax.odlib.utils

object ODSettings {

    const val brokerPort : Int = 50051
    const val workerPort : Int = 50053
    const val schedulerPort : Int = 50055

    const val cloudIp: String = "odcloud.duckdns.org"

    const val grpcMaxMessageSize: Int = 150000000
    const val RTTHistorySize: Int = 5
    const val pingTimeout: Long = 10000L // 15s
    const val RTTDelayMillis: Long = 10000L // 10s
    const val pingPayloadSize: Int = 32000 // 32Kb
    const val averageComputationTimesToStore: Int = 10
    const val workingThreads: Int = 1
    const val workerStatusUpdateInterval: Long = 1000 // 5s
    const val AUTO_STATUS_UPDATE_INTERVAL_MS: Long = 1000 // 5s
    const val RTTDelayMillisFailRetry: Long = 500 // 0.5s
    const val RTTDelayMillisFailAttempts: Long = 5

    var MY_ID : String = ""

    /*const val grpcTimeout: Long = 10 //Seconds
    const val grpcLongTimeout: Long = 120 //Seconds
    const val grpcShortTimeout: Long = 500 //Milliseconds*/
}