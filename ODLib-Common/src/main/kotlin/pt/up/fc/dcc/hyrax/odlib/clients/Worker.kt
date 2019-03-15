package pt.up.fc.dcc.hyrax.odlib.clients

import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.odlib.enums.BatteryStatus
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
    var batteryStatus : BatteryStatus = BatteryStatus.CHARGED

    private val smartTimer: Timer = Timer()
    private var circularFIFO: CircularFifoQueue<Int> = CircularFifoQueue(ODSettings.RTTHistorySize)
    var calculatedAvgLatency: Int = 0
    //private var pingFuture : ListenableFuture<ODProto.Ping>? = null
    private var consecutiveFailedPing = 0


    constructor(proto: ODProto.Worker?, address: String) : this(proto!!.id, address){
        updateStatus(proto)
    }

    internal fun updateStatus(proto: ODProto.Worker?) {
        battery = proto!!.battery
        avgComputingEstimate = proto.avgTimePerJob
        runningJobs = proto.runningJobs
        cpuCores = proto.cpuCores
        queueSize = proto.queueSize
        batteryStatus = BatteryStatus.valueOf(proto.batteryStatus.toString())

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

    internal fun getProto() : ODProto.Worker? {
        val worker = ODProto.Worker.newBuilder()
        worker.id = id
        worker.battery = battery
        worker.avgTimePerJob = avgComputingEstimate
        worker.cpuCores = cpuCores
        worker.queueSize = queueSize
        worker.runningJobs = runningJobs
        worker.type = type
        worker.bandwidthEstimate = calculatedAvgLatency
        return worker.build()
    }


    private fun addRTT(millis: Int) {
        circularFIFO.add(millis)
        calculatedAvgLatency = circularFIFO.sum()/circularFIFO.size
    }

    fun getAvgRTT() : Int {
        return calculatedAvgLatency
    }

    fun doRTTEstimates() {
        smartTimer.scheduleAtFixedRate(RTTTimer(), 0L, ODSettings.RTTDelayMillis)
    }

    fun stopRTTEstimates() {
        smartTimer.cancel()
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