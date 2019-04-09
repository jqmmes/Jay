package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.structures.ODJob
import pt.up.fc.dcc.hyrax.odlib.structures.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * https://github.com/grpc/grpc/blob/master/doc/connectivity-semantics-and-api.md
 * TRANSIENT_FAILURE
 *
 */

class BrokerGRPCClient(host: String) : GRPCClientBase<BrokerServiceGrpc.BrokerServiceBlockingStub, BrokerServiceGrpc.BrokerServiceFutureStub>
(host, ODSettings.brokerPort) {
    override var blockingStub: BrokerServiceGrpc.BrokerServiceBlockingStub = BrokerServiceGrpc.newBlockingStub(channel)
    override var futureStub: BrokerServiceGrpc.BrokerServiceFutureStub = BrokerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = BrokerServiceGrpc.newBlockingStub(channel)
        futureStub = BrokerServiceGrpc.newFutureStub(channel)
    }

    fun scheduleJob(job: ODJob, callback: ((ODProto.Results) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.scheduleJob(job.getProto())
        call.addListener(Runnable{ callback?.invoke(call.get()) }, AbstractODLib.executorPool)//{ J -> AbstractODLib.put(J) })
    }

    fun executeJob(job: ODProto.Job?, callback: ((ODProto.Results) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.executeJob(job)
        call.addListener(Runnable{ println(callback?.invoke(call.get()))}, AbstractODLib.executorPool)
    }

    fun ping(payload: Int, reply: Boolean = false, timeout: Long = 15000, callback: ((Int) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        println("BrokerServiceGrpc::ping ${channel.getState(false)}")
        AbstractODLib.executorPool.submit {
            val timer = System.currentTimeMillis()
            try {
                @Suppress("UNUSED_VARIABLE") val result = blockingStub
                        .withDeadlineAfter(timeout, TimeUnit.MILLISECONDS)
                        .ping(ODProto.Ping.newBuilder().setData(ByteString.copyFrom(ByteArray(payload))).setReply(reply).build())
                callback?.invoke((System.currentTimeMillis() - timer).toInt())
            } catch (e: TimeoutException) {
                callback?.invoke(-1)
            }
        }
    }

    fun updateWorkers(callback: (() -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.updateWorkers(Empty.getDefaultInstance())
        call.addListener(Runnable{ call.get(); callback() }, AbstractODLib.executorPool)
    }

    fun selectModel(model: ODModel, callback: ((ODProto.Status) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.setModel(model.getProto())
        call.addListener(Runnable{ callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun getModels(callback: ((Set<ODModel>) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.getModels(Empty.getDefaultInstance())
        call.addListener(Runnable{ try { callback?.invoke(ODUtils.parseModels(call.get())) }
        catch (e: ExecutionException) { println("getModels Unavailable") }
        }, AbstractODLib.executorPool)
    }

    fun getSchedulers(callback: ((Set<Pair<String, String>>) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.getSchedulers(Empty.getDefaultInstance())
        call.addListener(Runnable{
            try { callback?.invoke(ODUtils.parseSchedulers(call.get())) }
            catch (e: ExecutionException) { println("getSchedulers Unavailable") }
        }, AbstractODLib.executorPool)
    }

    fun setScheduler(id: String, callback: ((Boolean) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.setScheduler(ODProto.Scheduler.newBuilder().setId(id).build())
        call.addListener(Runnable { callback(call.get().code == ODProto.StatusCode.Success) }, AbstractODLib.executorPool)
    }

    fun advertiseWorkerStatus(request: ODProto.Worker?, completeCallback: () -> Unit) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.advertiseWorkerStatus(request)
        call.addListener(Runnable { completeCallback() }, AbstractODLib.executorPool)
    }

    fun diffuseWorkerStatus(request: ODProto.Worker?) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.diffuseWorkerStatus(request)
        call.addListener(Runnable { println("diffuseWorkerStatus Status: ${call.get().code.name}") }, AbstractODLib.executorPool)
    }

    fun requestWorkerStatus(callback: ((ODProto.Worker?) -> Unit)){
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.requestWorkerStatus(Empty.getDefaultInstance())
        call.addListener(Runnable { try {callback(call.get())} catch (e: ExecutionException) {callback(null) } }, AbstractODLib.executorPool)
    }


    fun listenMulticastWorkers(stopListener: Boolean = false, callback: ((ODProto.Status) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.listenMulticast(BoolValue.of(stopListener))
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun announceMulticast() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.announceMulticast(Empty.getDefaultInstance())
        call.addListener(Runnable { println("announceMulticast Status: ${call.get().code.name}") }, AbstractODLib.executorPool)
    }

    fun enableHearBeats(workerTypes: ODProto.WorkerTypes, callback: ((ODProto.Status) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.enableHearBeats(workerTypes)
        call.addListener(Runnable { println("enableHearBeats Status: ${call.get().code.name}"); callback(call.get()) }, AbstractODLib.executorPool)
    }

    fun enableBandwidthEstimates(bandwidthEstimateConfig: ODProto.BandwidthEstimate) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.enableBandwidthEstimates(bandwidthEstimateConfig)
        call.addListener(Runnable { println("enableBandwidthEstimates Status: ${call.get().code.name}") }, AbstractODLib.executorPool)
    }

    fun disableHearBeats() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.disableHearBeats(Empty.getDefaultInstance())
        call.addListener(Runnable { println("disableHearBeats Status: ${call.get().code.name}") }, AbstractODLib.executorPool)
    }

    fun disableBandwidthEstimates() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.disableBandwidthEstimates(Empty.getDefaultInstance())
        call.addListener(Runnable { println("disableBandwidthEstimates Status: ${call.get().code.name}") }, AbstractODLib.executorPool)
    }

    fun updateSmartSchedulerWeights(computeTime: Float, queueSize: Float, runningJobs: Float, battery: Float,
                                    bandwidth: Float, callback: ((ODProto.Status?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            callback(ODUtils.genStatus(ODProto.StatusCode.Error))
        }
        val call = futureStub.updateSmartSchedulerWeights(ODProto.Weights.newBuilder().setComputeTime(computeTime)
                .setQueueSize(queueSize).setRunningJobs(runningJobs).setBattery(battery).setBandwidth(bandwidth)
                .build())
        call.addListener(Runnable { callback(call.get()) }, AbstractODLib.executorPool)
    }
}