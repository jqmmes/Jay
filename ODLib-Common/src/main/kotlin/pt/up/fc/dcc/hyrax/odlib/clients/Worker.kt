package pt.up.fc.dcc.hyrax.odlib.clients

import com.google.common.util.concurrent.ListenableFuture
import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.*

class Worker(val id: String = UUID.randomUUID().toString(), address: String){

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

    val grpc: BrokerGRPCClient = BrokerGRPCClient(address)

    var rttEstimate: Long = 0L
    var avgComputingEstimate: Long = 0L

    var battery = 100
    var cpuCores = 0
    var queueSize = 1L
    var runningJobs = 0L

    private val smartTimer: Timer = Timer()
    private var circularFIFO: CircularFifoQueue<Long> = CircularFifoQueue(ODSettings.RTTHistorySize)
    private var calculatedAvgLatency: Long = 0L
    private var pingFuture : ListenableFuture<ODProto.Ping>? = null
    private var consecutiveFailedPing = 0


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
            //for (client in ClientManager.getRemoteODClients()) {
            if (pingFuture != null) {
                if (!pingFuture!!.isDone) {
                    pingFuture!!.cancel(true)
                    consecutiveFailedPing++
                } else {
                    consecutiveFailedPing = 0
                }
            }
            pingFuture = grpc.ping(callback = {T -> addRTT(T)})
            //RTTClient.measureRTT(client, ODSettings.rttPort)
            //}
            smartTimer.schedule(this, ODSettings.RTTDelayMillis)
        }
    }
}