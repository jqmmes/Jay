package pt.up.fc.dcc.hyrax.odlib.utils

object ODSettings {

    var BANDWIDTH_ESTIMATE_CALC_METHOD: String = "mean"
    var brokerPort : Int = 50051
    var workerPort : Int = 50053
    var schedulerPort : Int = 50055

    //var CLOUD_IP: String = "odcloud.duckdns.org"

    var GRPC_MAX_MESSAGE_SIZE: Int = 150000000
    var RTTHistorySize: Int = 5
    var pingTimeout: Long = 10000L // 15s
    var RTTDelayMillis: Long = 10000L // 10s
    var PING_PAYLOAD_SIZE: Int = 32000 // 32Kb
    var averageComputationTimesToStore: Int = 10
    var workingThreads: Int = 1
    var workerStatusUpdateInterval: Long = 5000 // 5s
    var AUTO_STATUS_UPDATE_INTERVAL_MS: Long = 5000 // 5s
    var RTTDelayMillisFailRetry: Long = 500 // 0.5s
    var RTTDelayMillisFailAttempts: Long = 5
    var MCAST_INTERFACE: String? = null

    var DEVICE_ID : String = ""

    var BANDWIDTH_ESTIMATE_TYPE = "ACTIVE" // ACTIVE/PASSIVE/ALL

    /*var grpcTimeout: Long = 10 //Seconds
    var grpcLongTimeout: Long = 120 //Seconds
    var grpcShortTimeout: Long = 500 //Milliseconds*/
}