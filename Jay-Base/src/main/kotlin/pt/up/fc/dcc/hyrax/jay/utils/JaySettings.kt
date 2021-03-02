/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.jay.utils

object JaySettings {

    var DEADLINE_CHECK_TOLERANCE: Int = 0
    var READ_RECORDED_PROFILE_DATA: Boolean = true
    var RESULTS_CIRCULAR_FIFO_SIZE: Int = 40
    var CPU_TO_BAT_CURRENT_CIRCULAR_FIFO_SIZE: Int = 40
    var CPU_TO_BAT_POWER_CIRCULAR_FIFO_SIZE: Int = 40
    var JAY_STATE_TO_CPU_CIRCULAR_FIFO_SIZE: Int = 40

    const val BLOCKING_STUB_DEADLINE: Long = 250

    var SINGLE_REMOTE_IP: String = "0.0.0.0"
    var CLOUDLET_ID = ""

    var ADVERTISE_WORKER_STATUS: Boolean = false

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

    var USE_FIXED_POWER_ESTIMATIONS: Boolean = false

    // Green Scheduler deadline broken decision
    var TASK_DEADLINE_BROKEN_SELECTION: String = "FASTER_COMPLETION"
    var INCLUDE_IDLE_COSTS: Boolean = false

    var USE_CPU_ESTIMATIONS = false

    // Task Caching
    // todo: Make use of cache
    var CACHE_TASKS: Boolean = true
    var KEEP_CACHED_TASK: Boolean = true
}