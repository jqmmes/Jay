package pt.up.fc.dcc.hyrax.jay.structures

import io.grpc.ConnectivityState
import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.BatteryStatus
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings.PING_PAYLOAD_SIZE
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * todo: Adapt Worker to provide relevant information about energy on specific states and other relevant system information
 */
class Worker(val id: String = UUID.randomUUID().toString(), val address: String,
             val type: JayProto.Worker.Type = JayProto.Worker.Type.REMOTE,
             checkHearBeat: Boolean = false, bwEstimates: Boolean = false,
             statusChangeCallback: ((Status) -> Unit)? = null) {

    enum class Status {
        ONLINE,
        OFFLINE
    }

    val grpc: BrokerGRPCClient = BrokerGRPCClient(address)

    private var avgComputingEstimate = 0L
    private var batteryLevel = 100
    private var batteryCapacity: Int = -1
    private var batteryStatus: BatteryStatus = BatteryStatus.UNKNOWN
    private var cpuCores = 0
    private var queueSize = 1
    private var queuedTasks = 0
    private var runningTasks = 0
    private var totalMemory = 0L
    private var freeMemory = 0L
    private var status = Status.OFFLINE
    private var bandwidthEstimate: Float = 0f
    private var avgResultSize = 0L
    //private var freeSpace = Long.MAX_VALUE
    //private var connections = 0

    private var smartPingScheduler: ScheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(1)
    private var circularFIFO: CircularFifoQueue<Float> = CircularFifoQueue(JaySettings.RTT_HISTORY_SIZE)
    private var circularResultsFIFO: CircularFifoQueue<Long> = CircularFifoQueue(JaySettings.RESULTS_CIRCULAR_FIFO_SIZE)
    private var consecutiveTransientFailurePing = 0
    private var proto: JayProto.Worker? = null
    private var autoStatusUpdateEnabledFlag = false
    private var autoStatusUpdateRunning = CountDownLatch(0)
    private var calcRTT = false
    private var checkingHeartBeat = false

    private var statusChangeCallback: ((Status) -> Unit)? = null

    private var lastStatusUpdateTimastamp: Long = -1


    constructor(proto: JayProto.Worker?, address: String, checkHearBeat: Boolean,
                bwEstimates: Boolean, statusChangeCallback: ((Status) -> Unit)? = null) : this(proto!!.id, address) {
        updateStatus(proto)
        if (checkHearBeat) enableHeartBeat(statusChangeCallback)
        if (bwEstimates && JaySettings.BANDWIDTH_ESTIMATE_TYPE in arrayOf("ACTIVE", "ALL"))
            doActiveRTTEstimates(statusChangeCallback = statusChangeCallback)
    }

    init {
        bandwidthEstimate = when (type) {
            JayProto.Worker.Type.LOCAL -> 0f
            JayProto.Worker.Type.REMOTE -> 0.05f
            JayProto.Worker.Type.CLOUD -> 0.1f
            else -> 0.07f
        }
        if (checkHearBeat) enableHeartBeat(statusChangeCallback)
        if (bwEstimates && JaySettings.BANDWIDTH_ESTIMATE_TYPE in arrayOf("ACTIVE", "ALL"))
            doActiveRTTEstimates(statusChangeCallback = statusChangeCallback)
        JayLogger.logInfo("INIT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
    }

    private fun genProto() {
        val worker = JayProto.Worker.newBuilder()
        worker.id = id // Internal
        worker.batteryLevel = batteryLevel // Modified by Worker
        worker.batteryCapacity = batteryCapacity
        worker.batteryStatus = batteryStatus
        worker.avgTimePerTask = avgComputingEstimate // Modified by Worker
        worker.cpuCores = cpuCores // Set by Worker
        worker.queueSize = queueSize // Set by Worker
        worker.queuedTasks = queuedTasks
        worker.runningTasks = runningTasks // Modified by Worker
        worker.type = type // Set in Broker
        worker.bandwidthEstimate = bandwidthEstimate // Set internally
        worker.totalMemory = totalMemory
        worker.freeMemory = freeMemory
        worker.avgResultSize = avgResultSize
        this.proto = worker.build()
    }

    private fun updateStatus(proto: JayProto.ProfileRecording?) {
        if (proto == null) return
        // todo implement getters and update worker relevant fields
    }

    private fun updateStatus(proto: JayProto.WorkerComputeStatus?) {
        if (proto == null) return
        runningTasks = proto.runningTasks
        avgComputingEstimate = proto.avgTimePerTask
        queueSize = proto.queueSize
        queuedTasks = proto.queuedTasks
    }

    internal fun updateStatus(computeProto: JayProto.WorkerComputeStatus?, profileProto: JayProto.ProfileRecording?): JayProto.Worker? {
        JayLogger.logInfo("INIT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
        if (computeProto == null && profileProto == null) return this.proto
        updateStatus(computeProto)
        updateStatus(profileProto)
        this.lastStatusUpdateTimastamp = System.currentTimeMillis()
        return getProto(true)
    }

    internal fun updateStatus(proto: JayProto.Worker?): JayProto.Worker? {
        JayLogger.logInfo("INIT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
        if (proto == null) return this.proto
        batteryLevel = proto.batteryLevel
        batteryCapacity = proto.batteryCapacity
        batteryStatus = proto.batteryStatus
        avgComputingEstimate = proto.avgTimePerTask
        runningTasks = proto.runningTasks
        cpuCores = proto.cpuCores
        queueSize = proto.queueSize
        queuedTasks = proto.queuedTasks
        totalMemory = proto.totalMemory
        freeMemory = proto.freeMemory
        this.lastStatusUpdateTimastamp = System.currentTimeMillis()
        return getProto(true)
    }

    internal fun getProto(genProto: Boolean = false): JayProto.Worker? {
        if (proto == null || genProto) genProto()
        return proto
    }

    /**
     * Request Worker Current Status Automatically. When receives the new status, updates this class information
     * Only request worker status when remote worker is online.
     */
    internal fun enableAutoStatusUpdate(updateNotificationCb: (JayProto.Worker?) -> Unit) {
        if (autoStatusUpdateEnabledFlag) return
        thread {
            autoStatusUpdateEnabledFlag = true
            autoStatusUpdateRunning = CountDownLatch(1)
            var backoffCount = 0
            do {
                if (grpc.channel.getState(true) != ConnectivityState.TRANSIENT_FAILURE) {
                    JayLogger.logInfo("REQUEST_WORKER_STATUS_INIT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
                    // Reduce a little bit the wait time because it takes time to update information and record last
                    if (isOnline() && System.currentTimeMillis() - this.lastStatusUpdateTimastamp >= JaySettings.WORKER_STATUS_UPDATE_INTERVAL * 0.8) {
                        grpc.requestWorkerStatus { W ->
                            JayLogger.logInfo("REQUEST_WORKER_STATUS_ONLINE", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
                            updateNotificationCb.invoke(updateStatus(W))
                            JayLogger.logInfo("REQUEST_WORKER_STATUS_COMPLETE", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
                        }
                    } else {
                        JayLogger.logInfo("REQUEST_WORKER_STATUS_OFFLINE", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
                    }
                } else {
                    if (++backoffCount % 5 == 0) grpc.channel.resetConnectBackoff()
                    JayLogger.logInfo("REQUEST_WORKER_STATUS_FAIL", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
                }
                sleep(JaySettings.WORKER_STATUS_UPDATE_INTERVAL)
            } while (autoStatusUpdateEnabledFlag)
            autoStatusUpdateRunning.countDown()
        }
    }

    internal fun disableAutoStatusUpdate(wait: Boolean = true) {
        autoStatusUpdateEnabledFlag = false
        if (wait) autoStatusUpdateRunning.await()
    }

    fun addResultSize(size: Long) {
        circularResultsFIFO.add(size)
        var tot = 0L
        circularResultsFIFO.forEach { tot += it }
        avgResultSize = tot / circularResultsFIFO.size
    }

    fun addRTT(millis: Int, payloadSize: Int = PING_PAYLOAD_SIZE) {
        bandwidthEstimate = JaySettings.BANDWIDTH_SCALING_FACTOR * if (JaySettings.BANDWIDTH_ESTIMATE_CALC_METHOD == "mean") {
            circularFIFO.add(millis.toFloat() / payloadSize)
            if (circularFIFO.size > 0) circularFIFO.sum() / circularFIFO.size else 0f
        } else {
            when {
                circularFIFO.size == 0 -> 0f
                circularFIFO.size % 2 == 0 -> (circularFIFO.sorted()[circularFIFO.size / 2] + circularFIFO.sorted()[(circularFIFO.size / 2) - 1]) / 2.0f
                else -> circularFIFO.sorted()[(circularFIFO.size - 1) / 2]
            }
        }
        JayLogger.logInfo("NEW_BANDWIDTH_ESTIMATE", actions = * arrayOf("WORKER_ID=$id", "BANDWIDTH_ESTIMATE=$bandwidthEstimate", "WORKER_TYPE=${type.name}"))
    }

    fun enableHeartBeat(statusChangeCallback: ((Status) -> Unit)? = null) {
        checkingHeartBeat = true
        this.statusChangeCallback = statusChangeCallback
        if (smartPingScheduler.isShutdown) smartPingScheduler = ScheduledThreadPoolExecutor(1)
        smartPingScheduler.schedule(RTTTimer(), 0L, TimeUnit.MILLISECONDS)
    }

    fun disableHeartBeat() {
        smartPingScheduler.shutdownNow()
        checkingHeartBeat = false
        statusChangeCallback = null
    }

    fun doActiveRTTEstimates(statusChangeCallback: ((Status) -> Unit)? = null) {
        calcRTT = true
        if (!checkingHeartBeat) enableHeartBeat(statusChangeCallback)
    }

    fun stopActiveRTTEstimates() {
        if (checkingHeartBeat) disableHeartBeat()
        calcRTT = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Worker

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun isOnline(): Boolean {
        return status == Status.ONLINE
    }

    fun getStatus(): Status {
        return status
    }

    private inner class RTTTimer : Runnable {
        override fun run() {
            grpc.ping(PING_PAYLOAD_SIZE, timeout = JaySettings.PING_TIMEOUT, callback = { T ->
                if (T == -1) {
                    if (status == Status.ONLINE) {
                        JayLogger.logInfo("HEARTBEAT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}", "STATUS=DEVICE_OFFLINE"))
                        status = Status.OFFLINE
                        statusChangeCallback?.invoke(status)
                    }
                    if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), JaySettings.RTT_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                } else if (T == -2 || T == -3) { // TRANSIENT_FAILURE || CONNECTING
                    if (status == Status.ONLINE) {
                        if (++consecutiveTransientFailurePing > JaySettings.RTT_DELAY_MILLIS_FAIL_ATTEMPTS) {
                            JayLogger.logInfo("HEARTBEAT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}", "STATUS=DEVICE_OFFLINE"))
                            status = Status.OFFLINE
                            statusChangeCallback?.invoke(status)
                            if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), JaySettings.RTT_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                            consecutiveTransientFailurePing = 0
                        } else {
                            if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), JaySettings.RTT_DELAY_MILLIS_FAIL_RETRY, TimeUnit.MILLISECONDS)
                        }
                    } else {
                        if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), JaySettings.RTT_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    }
                } else {
                    if (status == Status.OFFLINE) {
                        JayLogger.logInfo("HEARTBEAT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}", "STATUS=DEVICE_ONLINE"))
                        status = Status.ONLINE
                        statusChangeCallback?.invoke(status)
                    }
                    if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), JaySettings.RTT_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    if (calcRTT) addRTT(T)
                }
            })
        }
    }
}