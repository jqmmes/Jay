package pt.up.fc.dcc.hyrax.jay.utils

object JaySettings {

    var READ_SERVICE_DATA_INTERVAL: Long = 500
    const val BLOCKING_STUB_DEADLINE: Long = 150
    var SINGLE_REMOTE_IP: String = "0.0.0.0"
    var CLOUDLET_ID = ""
    var ADVERTISE_WORKER_STATUS: Boolean = false
    var BANDWIDTH_ESTIMATE_CALC_METHOD: String = "mean"
    var BANDWIDTH_SCALING_FACTOR: Float = 1.0f
    var BROKER_PORT: Int = 50051
    var WORKER_PORT: Int = 50053
    var SCHEDULER_PORT: Int = 50055
    var PROFILER_PORT: Int = 50057

    var GRPC_MAX_MESSAGE_SIZE: Int = 150000000
    var RTT_HISTORY_SIZE: Int = 5
    var PING_TIMEOUT: Long = 10000L // 15s
    var RTT_DELAY_MILLIS: Long = 10000L // 10s
    var PING_PAYLOAD_SIZE: Int = 32000 // 32Kb
    var AVERAGE_COMPUTATION_TIME_TO_SCORE: Int = 10
    var WORKING_THREADS: Int = 1
    var WORKER_STATUS_UPDATE_INTERVAL: Long = 5000 // 5s
    var AUTO_STATUS_UPDATE_INTERVAL_MS: Long = 5000 // 5s
    var RTTDelayMillisFailRetry: Long = 500 // 0.5s
    var RTTDelayMillisFailAttempts: Long = 5
    var MCAST_INTERFACE: String? = null

    var DEVICE_ID: String = ""

    var BANDWIDTH_ESTIMATE_TYPE = "ALL" // ACTIVE/PASSIVE/ALL
}