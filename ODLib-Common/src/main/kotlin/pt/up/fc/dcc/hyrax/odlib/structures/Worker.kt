package pt.up.fc.dcc.hyrax.odlib.structures

import io.grpc.ConnectivityState
import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker.BatteryStatus
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class Worker(val id: String = UUID.randomUUID().toString(), address: String, val type: ODProto.Worker.Type = ODProto.Worker.Type.REMOTE, checkHearBeat: Boolean = false, bwEstimates: Boolean = false, statusChangeCallback: ((Status) -> Unit)? = null){

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
    // TODO: Implement more variables
    //private var freeSpace = Long.MAX_VALUE
    //private var computationLoad = 0
    //private var connections = 0

    private var smartPingScheduler: ScheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(1)
    private var circularFIFO: CircularFifoQueue<Float> = CircularFifoQueue(ODSettings.RTTHistorySize)
    private var consecutiveTransientFailurePing = 0
    private var proto : ODProto.Worker? = null
    private var autoStatusUpdate = false
    private var autoStatusUpdateRunning = CountDownLatch(0)
    private var pingPayloadSize = 0
    private var calcRTT = false
    private var checkingHeartBeat = false

    private var statusChangeCallback: ((Status) -> Unit)? = null
    private var workerInfoUpdateNotify: ((ODProto.Worker?) -> Unit)? = null



    constructor(proto: ODProto.Worker?, address: String, checkHearBeat: Boolean, bwEstimates: Boolean, statusChangeCallback: ((Status) -> Unit)? = null) : this(proto!!.id, address){
        updateStatus(proto)
        if (checkHearBeat) enableHeartBeat(statusChangeCallback)
        if (bwEstimates) doActiveRTTEstimates(statusChangeCallback=statusChangeCallback)
    }

    init {
        bandwidthEstimate = when (type) {
            ODProto.Worker.Type.LOCAL -> 0f
            ODProto.Worker.Type.REMOTE -> 15f
            ODProto.Worker.Type.CLOUD -> 50f
            else -> 15f
        }
        if (checkHearBeat) enableHeartBeat(statusChangeCallback)
        if (bwEstimates) doActiveRTTEstimates(statusChangeCallback=statusChangeCallback)
        ODLogger.logInfo("Worker, INIT, WORKER_ID=$id, WORKER_TYPE=${type.name}")
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
        ODLogger.logInfo("Worker, UPDATE_STATUS, WORKER_ID=$id")
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

    internal fun enableAutoStatusUpdate(updateNotify: (ODProto.Worker?) -> Unit) {
        workerInfoUpdateNotify = updateNotify
        if (autoStatusUpdate) return
        thread {
            autoStatusUpdate = true
            autoStatusUpdateRunning = CountDownLatch(1)
            var backoffCount = 0
            do {
                if (grpc.channel.getState(true) != ConnectivityState.TRANSIENT_FAILURE) {
                    ODLogger.logInfo("Worker, REQUEST_WORKER_STATUS, INIT, WORKER_ID=$id")
                    if (isOnline()) grpc.requestWorkerStatus { W ->
                        ODLogger.logInfo("Worker, REQUEST_WORKER_STATUS, ONLINE, WORKER_ID=$id")
                        workerInfoUpdateNotify?.invoke(updateStatus(W))
                        ODLogger.logInfo("Worker, REQUEST_WORKER_STATUS, COMPLETE, WORKER_ID=$id")
                    } else {
                        ODLogger.logInfo("Worker, REQUEST_WORKER_STATUS, OFFLINE, WORKER_ID=$id")
                    }
                } else {
                    if (++backoffCount % 5 == 0) grpc.channel.resetConnectBackoff()
                    ODLogger.logInfo("Worker, REQUEST_WORKER_STATUS, FAIL, WORKER_ID=$id")
                }
                sleep(ODSettings.AUTO_STATUS_UPDATE_INTERVAL_MS)
            } while (autoStatusUpdate)
            autoStatusUpdateRunning.countDown()
        }
    }

    internal fun disableAutoStatusUpdate(wait: Boolean = true) {
        autoStatusUpdate = false
        if (wait) autoStatusUpdateRunning.await()
    }

    private fun addRTT(millis: Int) {
        circularFIFO.add(millis.toFloat()/pingPayloadSize)
        bandwidthEstimate = if (circularFIFO.size > 0) circularFIFO.sum()/circularFIFO.size else 0f
        ODLogger.logInfo("Worker, NEW_BANDWIDTH_ESTIMATE, WORKER_ID=$id, BANDWIDTH_ESTIMATE=$bandwidthEstimate")
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

    fun doActiveRTTEstimates(payload: Int = ODSettings.pingPayloadSize, statusChangeCallback: ((Status) -> Unit)? = null) {
        pingPayloadSize = payload
        calcRTT = true
        if (!checkingHeartBeat) enableHeartBeat(statusChangeCallback)
    }

    fun stopActiveRTTEstimates() {
        if (checkingHeartBeat) disableHeartBeat()
        pingPayloadSize = 0
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

    private inner class RTTTimer : Runnable {
        override fun run() {
            grpc.ping(pingPayloadSize, timeout = ODSettings.pingTimeout, callback = { T ->
                if (T == -1) {
                    if (status == Status.ONLINE) {
                        ODLogger.logInfo("Worker, HEARTBEAT, WORKER_ID=$id, DEVICE_OFFLINE")
                        status = Status.OFFLINE
                        statusChangeCallback?.invoke(status)
                    }
                    if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillis, TimeUnit.MILLISECONDS)
                } else if (T == -2) { // TRANSIENT_FAILURE
                    if (status == Status.ONLINE) {
                        if (++consecutiveTransientFailurePing > ODSettings.RTTDelayMillisFailAttempts) {
                            ODLogger.logInfo("Worker, HEARTBEAT, WORKER_ID=$id, DEVICE_OFFLINE")
                            status = Status.OFFLINE
                            statusChangeCallback?.invoke(status)
                            if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillis, TimeUnit.MILLISECONDS)
                            consecutiveTransientFailurePing = 0
                        } else {
                            if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillisFailRetry, TimeUnit.MILLISECONDS)
                        }
                    } else { if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillis, TimeUnit.MILLISECONDS) }

                } else if (T == -3) { // CONNECTING
                    if (status == Status.ONLINE) {
                        if (++consecutiveTransientFailurePing > ODSettings.RTTDelayMillisFailAttempts) {
                            ODLogger.logInfo("Worker, HEARTBEAT, WORKER_ID=$id, DEVICE_OFFLINE")
                            status = Status.OFFLINE
                            statusChangeCallback?.invoke(status)
                            if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillis, TimeUnit.MILLISECONDS)
                            consecutiveTransientFailurePing = 0
                        } else { if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillisFailRetry, TimeUnit.MILLISECONDS) }
                    } else { if (!smartPingScheduler.isShutdown) smartPingScheduler.schedule(RTTTimer(), ODSettings.RTTDelayMillis, TimeUnit.MILLISECONDS) }
                } else {
                    if (status == Status.OFFLINE) {
                        ODLogger.logInfo("Worker, HEARTBEAT, WORKER_ID=$id, DEVICE_ONLINE")
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