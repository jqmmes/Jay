package pt.up.fc.dcc.hyrax.odlib.clients

import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.odlib.enums.BatteryStatus
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.*

class Worker(val id: String = UUID.randomUUID().toString(), address: String){

    val grpc: BrokerGRPCClient = BrokerGRPCClient(address)

    //var rttEstimate: Long = 0
    var avgComputingEstimate = 0
    var battery = 100
    var cpuCores = 0
    var queueSize = 1
    var runningJobs = 0
    var batteryStatus : BatteryStatus = BatteryStatus.CHARGED

    private val smartTimer: Timer = Timer()
    private var circularFIFO: CircularFifoQueue<Long> = CircularFifoQueue(ODSettings.RTTHistorySize)
    private var calculatedAvgLatency: Long = 0L
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
        //deviceStatus.batteryStatus = batteryStatus.status
        val worker = ODProto.Worker.newBuilder()
        worker.battery = battery
        worker.avgTimePerJob = avgComputingEstimate
        worker.cpuCores = cpuCores
        worker.queueSize = queueSize
        worker.runningJobs = runningJobs
        return worker.build()
    }


    private fun addRTT(millis: Long) {
        circularFIFO.add(millis)
        calculatedAvgLatency = circularFIFO.sum()/circularFIFO.size
    }

    fun getAvgRTT() : Long {
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
                if (T == -1L) consecutiveFailedPing++
                else {
                    consecutiveFailedPing = 0
                    addRTT(T)
                }
            })

            smartTimer.schedule(this, ODSettings.RTTDelayMillis)
        }
    }
}