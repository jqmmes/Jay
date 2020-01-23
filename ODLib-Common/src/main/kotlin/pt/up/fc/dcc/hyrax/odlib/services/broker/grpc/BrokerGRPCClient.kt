package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.structures.Job
import pt.up.fc.dcc.hyrax.odlib.structures.Model
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

@Suppress("DuplicatedCode")
class BrokerGRPCClient(host: String) : GRPCClientBase<BrokerServiceGrpc.BrokerServiceBlockingStub, BrokerServiceGrpc.BrokerServiceFutureStub>
(host, ODSettings.BROKER_PORT) {
    override var blockingStub: BrokerServiceGrpc.BrokerServiceBlockingStub = BrokerServiceGrpc.newBlockingStub(channel)
    override var futureStub: BrokerServiceGrpc.BrokerServiceFutureStub = BrokerServiceGrpc.newFutureStub(channel)
    private var asyncStub: BrokerServiceGrpc.BrokerServiceStub = BrokerServiceGrpc.newStub(channel)

    override fun reconnectStubs() {
        blockingStub = BrokerServiceGrpc.newBlockingStub(channel)
        futureStub = BrokerServiceGrpc.newFutureStub(channel)
    }

    fun scheduleJob(job: Job, callback: ((ODProto.Results) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.scheduleJob(job.getProto())
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun executeJob(job: ODProto.Job?, callback: ((ODProto.Results) -> Unit)? = null, schedulerInformCallback: (() -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val jobId = job?.id ?: ""
        AbstractODLib.executorPool.submit {
            val startTime = System.currentTimeMillis()
            ODLogger.logInfo("INIT", jobId)
            asyncStub.executeJob(job, object : StreamObserver<ODProto.Results> {
                private var lastResult : ODProto.Results? = null
                override fun onNext(results: ODProto.Results) {
                    lastResult = results
                    when (results.status) {
                        ODProto.StatusCode.Received -> ODLogger.logInfo("DATA_REACHED_SERVER", jobId, "DATA_SIZE=${job?.toByteArray()?.size};DURATION_MILLIS=${startTime-System.currentTimeMillis()}")
                        ODProto.StatusCode.Success -> ODLogger.logInfo("EXECUTION_COMPLETE", jobId, "DATA_SIZE=${job?.toByteArray()?.size};DURATION_MILLIS=${startTime-System.currentTimeMillis()}")
                        else -> onError(Throwable("Error Received onNext for jobId: $jobId"))
                    }
                    if (ODSettings.BANDWIDTH_ESTIMATE_TYPE in arrayOf("PASSIVE", "ALL") && results.status == ODProto.StatusCode.Received) {
                        BrokerService.passiveBandwidthUpdate(jobId, job?.toByteArray()?.size ?: -1, System.currentTimeMillis()-startTime)
                    } else if (results.status == ODProto.StatusCode.Error) {
                        onError(Throwable("Error Received onNext for jobId: $jobId"))
                    }
                }

                override fun onError(t: Throwable) {
                    ODLogger.logError("ERROR", jobId)
                    callback?.invoke(ODProto.Results.getDefaultInstance()); ODLogger.logError("ERROR", jobId)
                    t.printStackTrace()
                }

                override fun onCompleted() {
                    ODLogger.logInfo("COMPLETE", jobId, "DURATION_MILLIS=${startTime-System.currentTimeMillis()}")
                    callback?.invoke(lastResult ?: ODProto.Results.getDefaultInstance())
                    schedulerInformCallback?.invoke()
                }
            })
        }
    }

     fun ping(payload: Int, reply: Boolean = false, timeout: Long = 15000, callback: ((Int) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            callback?.invoke(-2)
            return
        }
        if (channel.getState(true) == ConnectivityState.CONNECTING) {
            callback?.invoke(-3)
            return
        }
        AbstractODLib.executorPool.submit {
            val timer = System.currentTimeMillis()
            try {
                val pingData = blockingStub
                        .withDeadlineAfter(timeout, TimeUnit.MILLISECONDS)
                        .ping(ODProto.Ping.newBuilder().setData(ByteString.copyFrom(ByteArray(payload))).setReply(reply).build())
                ODLogger.logInfo("SEND_PING", actions = *arrayOf("DATA_SIZE=${pingData.data.size()}"))
                callback?.invoke((System.currentTimeMillis() - timer).toInt())
            } catch (e: TimeoutException) {
                ODLogger.logError("TIMEOUT")
                callback?.invoke(-1)
            }
        }
    }

    fun updateWorkers(callback: (() -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.updateWorkers(Empty.getDefaultInstance())
        call.addListener(Runnable{ try { call.get(); callback() } catch (e: Exception) {callback()} }, AbstractODLib.executorPool)
    }

    fun selectModel(model: Model, callback: ((ODProto.Status) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.setModel(model.getProto())
        call.addListener(Runnable{ callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun getModels(callback: ((Set<Model>) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.getModels(Empty.getDefaultInstance())
        call.addListener(Runnable{ try { callback?.invoke(ODUtils.parseModels(call.get())) }
        catch (e: ExecutionException) { ODLogger.logError("UNAVAILABLE") }
        }, AbstractODLib.executorPool)
    }

    fun getSchedulers(callback: ((Set<Pair<String, String>>) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.getSchedulers(Empty.getDefaultInstance())
        call.addListener(Runnable{
            try { callback?.invoke(ODUtils.parseSchedulers(call.get())) }
            catch (e: ExecutionException) { ODLogger.logError("UNAVAILABLE") }
        }, AbstractODLib.executorPool)
    }

    fun setScheduler(id: String, callback: ((Boolean) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.setScheduler(ODProto.Scheduler.newBuilder().setId(id).build())
        call.addListener(Runnable { try {callback(call.get().code == ODProto.StatusCode.Success)} catch(e: ExecutionException) {callback(false)} }, AbstractODLib.executorPool)
    }

    fun advertiseWorkerStatus(request: ODProto.Worker?, completeCallback: () -> Unit) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.advertiseWorkerStatus(request)
        call.addListener(Runnable { completeCallback() }, AbstractODLib.executorPool)
    }

    fun diffuseWorkerStatus(request: ODProto.Worker?) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.diffuseWorkerStatus(request)
        call.addListener(Runnable { try {ODLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}"))} catch(e: ExecutionException){} }, AbstractODLib.executorPool)
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

    /*fun announceMulticast() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.announceMulticast(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                ODLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}"))
            } catch (e: ExecutionException) {
                ODLogger.logError("ERROR")
            }}, AbstractODLib.executorPool)
    }*/

    fun enableHearBeats(workerTypes: ODProto.WorkerTypes, callback: ((ODProto.Status) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.enableHearBeats(workerTypes)
        call.addListener(Runnable { ODLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")); callback(call.get()) }, AbstractODLib.executorPool)
    }

    fun enableBandwidthEstimates(bandwidthEstimateConfig: ODProto.BandwidthEstimate, callback: ((ODProto.Status) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.enableBandwidthEstimates(bandwidthEstimateConfig)
        call.addListener(Runnable { ODLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")); callback(call.get()) }, AbstractODLib.executorPool)
    }

    fun disableHearBeats() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.disableHearBeats(Empty.getDefaultInstance())
        call.addListener(Runnable { ODLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")) }, AbstractODLib.executorPool)
    }

    fun disableBandwidthEstimates() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.disableBandwidthEstimates(Empty.getDefaultInstance())
        call.addListener(Runnable { ODLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")) }, AbstractODLib.executorPool)
    }

    fun updateSmartSchedulerWeights(computeTime: Float, queueSize: Float, runningJobs: Float, battery: Float,
                                    bandwidth: Float, callback: ((ODProto.Status?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            callback(ODUtils.genStatusError())
        }
        val call = futureStub.updateSmartSchedulerWeights(ODProto.Weights.newBuilder().setComputeTime(computeTime)
                .setQueueSize(queueSize).setRunningJobs(runningJobs).setBattery(battery).setBandwidth(bandwidth)
                .build())
        call.addListener(Runnable { try { callback(call.get()) } catch (e: RuntimeException) {callback(ODUtils.genStatusError())} }, AbstractODLib.executorPool)
    }

    fun announceServiceStatus(serviceStatus: ODProto.ServiceStatus, callback: ((ODProto.Status?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE ) {
            channel.resetConnectBackoff()
            callback(ODUtils.genStatusError())
        }
        try {
            val call = futureStub.announceServiceStatus(serviceStatus)
            call.addListener(Runnable {
                try {
                    callback.invoke(call.get())
                } catch (e: Exception) { }
            }, AbstractODLib.executorPool)
        } catch (e: StatusRuntimeException) { }
    }

    fun stopService(callback: (ODProto.Status?) -> Unit) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) callback(ODUtils.genStatusError())
        val call = futureStub.stopService(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback(call.get())
            } catch (e: Exception) {
                callback(ODUtils.genStatusError())
            }
        }, AbstractODLib.executorPool)
    }
}