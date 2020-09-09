package pt.up.fc.dcc.hyrax.jay.utils

object JaySettings {

    var READ_RECORDED_PROFILE_DATA: Boolean = true
    var RESULTS_CIRCULAR_FIFO_SIZE: Int = 20
    var CPU_TO_BAT_CURRENT_CIRCULAR_FIFO_SIZE: Int = 20
    var CPU_TO_BAT_POWER_CIRCULAR_FIFO_SIZE: Int = 20
    var JAY_STATE_TO_CPU_CIRCULAR_FIFO_SIZE: Int = 20

    const val BLOCKING_STUB_DEADLINE: Long = 150

    var SINGLE_REMOTE_IP: String = "0.0.0.0"
    var CLOUDLET_ID = ""

    var ADVERTISE_WORKER_STATUS: Boolean = true

    var BANDWIDTH_ESTIMATE_CALC_METHOD: String = "mean"
    var BANDWIDTH_SCALING_FACTOR: Float = 1.0f

    var BROKER_PORT: Int = 45923
    var WORKER_PORT: Int = 62508
    var SCHEDULER_PORT: Int = 59052
    var PROFILER_PORT: Int = 60031

    var GRPC_MAX_MESSAGE_SIZE: Int = 150000000

    var PING_TIMEOUT: Long = 10000L // 15s
    var PING_PAYLOAD_SIZE: Int = 32000 // 32Kb

    var RTT_HISTORY_SIZE: Int = 5
    var RTT_DELAY_MILLIS: Long = 10000L // 10s
    var RTT_DELAY_MILLIS_FAIL_RETRY: Long = 500 // 0.5s
    var RTT_DELAY_MILLIS_FAIL_ATTEMPTS: Long = 5

    var AVERAGE_COMPUTATION_TIME_TO_SCORE: Int = 10
    var WORKING_THREADS: Int = 1


    var MULTICAST_INTERFACE: String? = null

    var MULTICAST_PKT_INTERVAL: Long = 500
    var READ_SERVICE_DATA_INTERVAL: Long = 500
    var WORKER_STATUS_UPDATE_INTERVAL: Long = 1000 // 1s

    var DEVICE_ID: String = ""

    var BANDWIDTH_ESTIMATE_TYPE = "ALL" // ACTIVE/PASSIVE/ALL


    // Calibration Settings
    var COMPUTATION_BASELINE_DURATION_FLAG = false
    var COMPUTATION_BASELINE_DURATION = 10000L // 10 minutes

    var TRANSFER_BASELINE_FLAG = false
    var TRANSFER_BASELINE_DURATION = 10000L // 10 minutes

    var USE_FIXED_POWER_ESTIMATIONS: Boolean = true
}