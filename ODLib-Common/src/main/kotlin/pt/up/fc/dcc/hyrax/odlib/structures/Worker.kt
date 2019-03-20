package pt.up.fc.dcc.hyrax.odlib.structures

import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker.BatteryStatus
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.*

class Worker(val id: String = UUID.randomUUID().toString(), address: String, val type: ODProto.Worker.Type = ODProto.Worker.Type.REMOTE){

    val grpc: BrokerGRPCClient = BrokerGRPCClient(address)

    //var rttEstimate: Long = 0
    var avgComputingEstimate = 0L
    var battery = 100
    var cpuCores = 0
    var queueSize = 1
    var runningJobs = 0
    var batteryStatus : ODProto.Worker.BatteryStatus = BatteryStatus.CHARGED
    var totalMemory = 0L
    var freeMemory = 0L
    var freeSpace = Long.MAX_VALUE // TODO
    var computationLoad = 0 // TODO
    var connections = 0 // TODO


    private val smartTimer: Timer = Timer()
    private var circularFIFO: CircularFifoQueue<Int> = CircularFifoQueue(ODSettings.RTTHistorySize)
    var bandwidthEstimate: Int = 0
    //private var pingFuture : ListenableFuture<ODProto.Ping>? = null
    private var consecutiveFailedPing = 0
    private var proto : ODProto.Worker? = null


    constructor(proto: ODProto.Worker?, address: String) : this(proto!!.id, address){
        updateStatus(proto)
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


    private fun addRTT(millis: Int) {
        circularFIFO.add(millis)
        bandwidthEstimate = if (circularFIFO.size > 0) circularFIFO.sum()/circularFIFO.size else 0
    }

    fun getAvgRTT() : Int {
        return bandwidthEstimate
    }

    fun doRTTEstimates() {
        smartTimer.scheduleAtFixedRate(RTTTimer(), 0L, ODSettings.RTTDelayMillis)
    }

    fun stopRTTEstimates() {
        smartTimer.cancel()
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

    private inner class RTTTimer : TimerTask() {
        override fun run() {
            grpc.ping(timeout = ODSettings.pingTimeout, callback = { T ->
                if (T == -1) consecutiveFailedPing++
                else {
                    consecutiveFailedPing = 0
                    addRTT(T)
                }
            })

            smartTimer.schedule(this, ODSettings.RTTDelayMillis)
        }
    }
}