package pt.up.fc.dcc.hyrax.odlib.structures

import io.grpc.ConnectivityState
import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker.BatteryStatus
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.CountDownLatch
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
    private var runningJobs = 0
    private var batteryStatus : ODProto.Worker.BatteryStatus = BatteryStatus.CHARGED
    private var totalMemory = 0L
    private var freeMemory = 0L
    private var status = Status.ONLINE
    private var bandwidthEstimate: Int = 0
    // TODO: Implement more variables
    //private var freeSpace = Long.MAX_VALUE
    //private var computationLoad = 0
    //private var connections = 0

    private val smartTimer: Timer = Timer()
    private var circularFIFO: CircularFifoQueue<Int> = CircularFifoQueue(ODSettings.RTTHistorySize)
    private var consecutiveFailedPing = 0
    private var proto : ODProto.Worker? = null
    private var autoStatusUpdate = false
    private var autoStatusUpdateRunning = CountDownLatch(0)
    private var pingPayloadSize = 0
    private var calcRTT = false
    private var checkingHeartBeat = false

    private var statusChangeCallback: ((Status) -> Unit)? = null


    constructor(proto: ODProto.Worker?, address: String, checkHearBeat: Boolean, bwEstimates: Boolean, statusChangeCallback: ((Status) -> Unit)? = null) : this(proto!!.id, address){
        updateStatus(proto)
        if (checkHearBeat) enableHeartBeat(statusChangeCallback)
        if (bwEstimates) doActiveRTTEstimates(statusChangeCallback=statusChangeCallback)
    }

    init {
        bandwidthEstimate = when (type) {
            ODProto.Worker.Type.LOCAL -> 0
            ODProto.Worker.Type.REMOTE -> 15
            ODProto.Worker.Type.CLOUD -> 50
            else -> 15
        }
        if (checkHearBeat) enableHeartBeat(statusChangeCallback)
        if (bwEstimates) doActiveRTTEstimates(statusChangeCallback=statusChangeCallback)
    }

    private fun genProto() {
        val worker = ODProto.Worker.newBuilder()
        worker.id = id // Internal
        worker.battery = battery // Modified by Worker
        worker.avgTimePerJob = avgComputingEstimate // Modified by Worker
        worker.cpuCores = cpuCores // Set by Worker
        worker.queueSize = queueSize // Set by Worker
        worker.runningJobs = runningJobs // Modified by Worker
        worker.type = type // Set in Broker
        worker.bandwidthEstimate = bandwidthEstimate // Set internally
        worker.totalMemory = totalMemory
        worker.freeMemory = freeMemory
        worker.bandwidthEstimate = bandwidthEstimate
        proto = worker.build()
    }

    internal fun updateStatus(proto: ODProto.Worker?) : ODProto.Worker? {
        if (proto == null) return this.proto
        battery = proto.battery
        avgComputingEstimate = proto.avgTimePerJob
        runningJobs = proto.runningJobs
        cpuCores = proto.cpuCores
        queueSize = proto.queueSize
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

    internal fun enableAutoStatusUpdate() {
        if (autoStatusUpdate) return
        thread {
            autoStatusUpdate = true
            autoStatusUpdateRunning = CountDownLatch(1)
            var backoffCount = 0
            do {
                if (grpc.channel.getState(true) != ConnectivityState.TRANSIENT_FAILURE)
                    grpc.requestWorkerStatus { W -> updateStatus(W) }
                else if(++backoffCount % 5 == 0) grpc.channel.resetConnectBackoff()
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
        circularFIFO.add(millis)
        bandwidthEstimate = if (circularFIFO.size > 0) circularFIFO.sum()/circularFIFO.size else 0
    }

    fun enableHeartBeat(statusChangeCallback: ((Status) -> Unit)? = null) {
        checkingHeartBeat = true
        this.statusChangeCallback = statusChangeCallback
        try { smartTimer.scheduleAtFixedRate(RTTTimer(), 0L, ODSettings.RTTDelayMillis) } catch (ignore: IllegalStateException) {}
    }

    fun disableHeartBeat() {
        smartTimer.cancel()
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

    private inner class RTTTimer : TimerTask() {
        override fun run() {
            grpc.ping(pingPayloadSize, timeout = ODSettings.pingTimeout, callback = { T ->
                if (T == -1) {
                    consecutiveFailedPing++
                    if (status == Status.ONLINE) {
                        status = Status.OFFLINE
                        statusChangeCallback?.invoke(status)
                    }
                } else {
                    if (status == Status.OFFLINE) {
                        status = Status.ONLINE
                        statusChangeCallback?.invoke(status)
                    }
                    consecutiveFailedPing = 0
                    if (calcRTT) addRTT(T)
                }
            })
            //smartTimer.schedule(this, ODSettings.RTTDelayMillis)
        }
    }
}