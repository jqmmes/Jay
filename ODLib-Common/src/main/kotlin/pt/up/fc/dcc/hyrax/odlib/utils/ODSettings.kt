package pt.up.fc.dcc.hyrax.odlib.utils

object ODSettings {

    var brokerPort : Int = 50051
    var workerPort : Int = 50053
    var schedulerPort : Int = 50055

    var cloudIp: String = "odcloud.duckdns.org"

    var grpcMaxMessageSize: Int = 150000000
    var RTTHistorySize: Int = 5
    var pingTimeout: Long = 10000L // 15s
    var RTTDelayMillis: Long = 10000L // 10s
    var pingPayloadSize: Int = 32000 // 32Kb
    var averageComputationTimesToStore: Int = 10
    var workingThreads: Int = 1
    var workerStatusUpdateInterval: Long = 5000 // 5s
    var AUTO_STATUS_UPDATE_INTERVAL_MS: Long = 5000 // 5s
    var RTTDelayMillisFailRetry: Long = 500 // 0.5s
    var RTTDelayMillisFailAttempts: Long = 5

    var MY_ID : String = ""

    /*var grpcTimeout: Long = 10 //Seconds
    var grpcLongTimeout: Long = 120 //Seconds
    var grpcShortTimeout: Long = 500 //Milliseconds*/
}