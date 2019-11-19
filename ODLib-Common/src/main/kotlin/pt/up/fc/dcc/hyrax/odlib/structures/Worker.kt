package pt.up.fc.dcc.hyrax.odlib.structures

import io.grpc.ConnectivityState
import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker.BatteryStatus
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings.PING_PAYLOAD_SIZE
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class Worker(val id: String = UUID.randomUUID().toString(), val address: String,
             val type: ODProto.Worker.Type = ODProto.Worker.Type.REMOTE,
             checkHearBeat: Boolean = false, bwEstimates: Boolean = false,
             statusChangeCallback: ((Status) -> Unit)? = null) {

    enum class Status {
        ONLINE,
        OFFLINE
    }

    val grpc: BrokerGRPCClient = BrokerGRPCClient(address)

    private var avgComputingEstimate = 0L
    private var battery = 100
    private var cpuCores = 0
    private var queueSize = 1
    private var queuedJobs = 0
    private var runningJobs = 0
    private var batteryStatus : BatteryStatus = BatteryStatus.CHARGED
    private var totalMemory = 0L
    private var freeMemory = 0L
    private var status = Status.OFFLINE
    private var bandwidthEstimate: Float = 0f
    //private var freeSpace = Long.MAX_VALUE
    //private var computationLoad = 0
    //private var connections = 0

    private var smartPingScheduler: ScheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(1)
    private var circularFIFO: CircularFifoQueue<Float> = CircularFifoQueue(ODSettings.RTTHistorySize)
    private var consecutiveTransientFailurePing = 0
    private var proto : ODProto.Worker? = null
    private var autoStatusUpdateEnabledFlag = false
    private var autoStatusUpdateRunning = CountDownLatch(0)
    private var calcRTT = false
    private var checkingHeartBeat = false

    private var statusChangeCallback: ((Status) -> Unit)? = null

    constructor(proto: ODProto.Worker?, address: String, checkHearBeat: Boolean,
                bwEstimates: Boolean, statusChangeCallback: ((Status) -> Unit)? = null) : this(proto!!.id, address) {
        updateStatus(proto)
        if (checkHearBeat) enableHeartBeat(statusChangeCallback)
        if (bwEstimates && ODSettings.BANDWIDTH_ESTIMATE_TYPE in arrayOf("ACTIVE", "ALL"))
            doActiveRTTEstimates(statusChangeCallback = statusChangeCallback)
    }

    init {
        bandwidthEstimate = when (type) {
            ODProto.Worker.Type.LOCAL -> 0f
            ODProto.Worker.Type.REMOTE -> 0.05f
            ODProto.Worker.Type.CLOUD -> 0.1f
            else -> 0.07f
        }
        if (checkHearBeat) enableHeartBeat(statusChangeCallback)
        if (bwEstimates && ODSettings.BANDWIDTH_ESTIMATE_TYPE in arrayOf("ACTIVE", "ALL"))
            doActiveRTTEstimates(statusChangeCallback = statusChangeCallback)
        ODLogger.logInfo("INIT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
    }

    private fun genProto() {
        val worker = ODProto.Worker.newBuilder()
        worker.id = id // Internal
        worker.battery = battery // Modified by Worker
        worker.avgTimePerJob = avgComputingEstimate // Modified by Worker
        worker.cpuCores = cpuCores // Set by Worker
        worker.queueSize = queueSize // Set by Worker
        worker.queuedJobs = queuedJobs
        worker.runningJobs = runningJobs // Modified by Worker
        worker.type = type // Set in Broker
        worker.bandwidthEstimate = bandwidthEstimate // Set internally
        worker.totalMemory = totalMemory
        worker.freeMemory = freeMemory
        proto = worker.build()
    }

    internal fun updateStatus(proto: ODProto.Worker?) : ODProto.Worker? {
        ODLogger.logInfo("INIT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
        if (proto == null) return this.proto
        battery = proto.battery
        avgComputingEstimate = proto.avgTimePerJob
        runningJobs = proto.runningJobs
        cpuCores = proto.cpuCores
        queueSize = proto.queueSize
        queuedJobs = proto.queuedJobs
        batteryStatus = proto.batteryStatus
        totalMemory = proto.totalMemory
        freeMemory = proto.freeMemory
        genProto()
        return getProto()
    }

    internal fun getProto() : ODProto.Worker? {
        if (proto == null) genProto()
        return proto
    }

    /**
     * Request Worker Current Status Automatically. When receives the new status, updates this class information
     * Only request worker status when remote worker is online.
     */
    internal fun enableAutoStatusUpdate(updateNotify: (ODProto.Worker?) -> Unit) {
        if (autoStatusUpdateEnabledFlag) return
        thread {
            autoStatusUpdateEnabledFlag = true
            autoStatusUpdateRunning = CountDownLatch(1)
            var backoffCount = 0
            do {
                if (grpc.channel.getState(true) != ConnectivityState.TRANSIENT_FAILURE) {
                    ODLogger.logInfo("REQUEST_WORKER_STATUS_INIT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
                    if (isOnline()) grpc.requestWorkerStatus { W ->
                        ODLogger.logInfo("REQUEST_WORKER_STATUS_ONLINE", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
                        updateNotify.invoke(updateStatus(W))
                        ODLogger.logInfo("REQUEST_WORKER_STATUS_COMPLETE", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
                    } else {
                        ODLogger.logInfo("REQUEST_WORKER_STATUS_OFFLINE", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
                    }
                } else {
                    if (++backoffCount % 5 == 0) grpc.channel.resetConnectBackoff()
                    ODLogger.logInfo("REQUEST_WORKER_STATUS_FAIL", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}"))
                }
                sleep(ODSettings.AUTO_STATUS_UPDATE_INTERVAL_MS)
            } while (autoStatusUpdateEnabledFlag)
            autoStatusUpdateRunning.countDown()
        }
    }

    internal fun disableAutoStatusUpdate(wait: Boolean = true) {
        autoStatusUpdateEnabledFlag = false
        if (wait) autoStatusUpdateRunning.await()
    }

    fun addRTT(millis: Int, payloadSize: Int = PING_PAYLOAD_SIZE) {
        bandwidthEstimate = ODSettings.BANDWIDTH_SCALING_FACTOR * if (ODSettings.BANDWIDTH_ESTIMATE_CALC_METHOD == "mean") {
            circularFIFO.add(millis.toFloat() / payloadSize)
            if (circularFIFO.size > 0) circularFIFO.sum() / circularFIFO.size else 0f
        } else {
            when {
                circularFIFO.size == 0 -> 0f
                circularFIFO.size % 2 == 0 -> (circularFIFO.sorted()[circularFIFO.size / 2] + circularFIFO.sorted()[(circularFIFO.size / 2) - 1]) / 2.0f
                else -> circularFIFO.sorted()[(circularFIFO.size - 1) / 2]
            }
        }
        ODLogger.logInfo("NEW_BANDWIDTH_ESTIMATE", actions = * arrayOf("WORKER_ID=$id", "BANDWIDTH_ESTIMATE=$bandwidthEstimate", "WORKER_TYPE=${type.name}"))
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
            grpc.ping(PING_PAYLOAD_SIZE, timeout = ODSettings.pingTimeout, callback = { T ->
                if (T == -1) {
                    if (status == Status.ONLINE) {
                        ODLogger.logInfo("HEARTBEAT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}", "STATUS=DEVICE_OFFLINE"))
                        status = Status.OFFLINE
                        statusChangeCallback?.invoke(status)
                    }
                    if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillis, TimeUnit.MILLISECONDS)
                } else if (T == -2 || T == -3) { // TRANSIENT_FAILURE || CONNECTING
                    if (status == Status.ONLINE) {
                        if (++consecutiveTransientFailurePing > ODSettings.RTTDelayMillisFailAttempts) {
                            ODLogger.logInfo("HEARTBEAT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}", "STATUS=DEVICE_OFFLINE"))
                            status = Status.OFFLINE
                            statusChangeCallback?.invoke(status)
                            if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillis, TimeUnit.MILLISECONDS)
                            consecutiveTransientFailurePing = 0
                        } else {
                            if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillisFailRetry, TimeUnit.MILLISECONDS)
                        }
                    } else { if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillis, TimeUnit.MILLISECONDS) }
                } else {
                    if (status == Status.OFFLINE) {
                        ODLogger.logInfo("HEARTBEAT", actions = * arrayOf("WORKER_ID=$id", "WORKER_TYPE=${type.name}", "STATUS=DEVICE_ONLINE"))
                        status = Status.ONLINE
                        statusChangeCallback?.invoke(status)
                    }
                    if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillis, TimeUnit.MILLISECONDS)
                    if (calcRTT) addRTT(T)
                }
            })
        }
    }
}