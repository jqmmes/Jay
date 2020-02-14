package pt.up.fc.dcc.hyrax.jay.services.broker.grpc

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import pt.up.fc.dcc.hyrax.jay.AbstractJay
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.protoc.BrokerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.jay.structures.Job
import pt.up.fc.dcc.hyrax.jay.structures.Model
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
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
(host, JaySettings.BROKER_PORT) {
    override var blockingStub: BrokerServiceGrpc.BrokerServiceBlockingStub = BrokerServiceGrpc.newBlockingStub(channel)
    override var futureStub: BrokerServiceGrpc.BrokerServiceFutureStub = BrokerServiceGrpc.newFutureStub(channel)
    private var asyncStub: BrokerServiceGrpc.BrokerServiceStub = BrokerServiceGrpc.newStub(channel)

    override fun reconnectStubs() {
        blockingStub = BrokerServiceGrpc.newBlockingStub(channel)
        futureStub = BrokerServiceGrpc.newFutureStub(channel)
    }

    fun scheduleJob(job: Job, callback: ((JayProto.Results) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.scheduleJob(job.getProto())
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractJay.executorPool)
    }

    fun executeJob(job: JayProto.Job?, callback: ((JayProto.Results) -> Unit)? = null, schedulerInformCallback: (() -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val jobId = job?.id ?: ""
        AbstractJay.executorPool.submit {
            val startTime = System.currentTimeMillis()
            JayLogger.logInfo("INIT", jobId)
            asyncStub.executeJob(job, object : StreamObserver<JayProto.Results> {
                private var lastResult: JayProto.Results? = null
                override fun onNext(results: JayProto.Results) {
                    lastResult = results
                    when (results.status) {
                        JayProto.StatusCode.Received -> JayLogger.logInfo("DATA_REACHED_SERVER", jobId, "DATA_SIZE=${job?.toByteArray()?.size};DURATION_MILLIS=${startTime - System.currentTimeMillis()}")
                        JayProto.StatusCode.Success -> JayLogger.logInfo("EXECUTION_COMPLETE", jobId, "DATA_SIZE=${job?.toByteArray()?.size};DURATION_MILLIS=${startTime - System.currentTimeMillis()}")
                        else -> onError(Throwable("Error Received onNext for jobId: $jobId"))
                    }
                    if (JaySettings.BANDWIDTH_ESTIMATE_TYPE in arrayOf("PASSIVE", "ALL") && results.status == JayProto.StatusCode.Received) {
                        BrokerService.passiveBandwidthUpdate(jobId, job?.toByteArray()?.size
                                ?: -1, System.currentTimeMillis() - startTime)
                    } else if (results.status == JayProto.StatusCode.Error) {
                        onError(Throwable("Error Received onNext for jobId: $jobId"))
                    }
                }

                override fun onError(t: Throwable) {
                    JayLogger.logError("ERROR", jobId)
                    callback?.invoke(JayProto.Results.getDefaultInstance()); JayLogger.logError("ERROR", jobId)
                    t.printStackTrace()
                }

                override fun onCompleted() {
                    JayLogger.logInfo("COMPLETE", jobId, "DURATION_MILLIS=${startTime - System.currentTimeMillis()}")
                    callback?.invoke(lastResult ?: JayProto.Results.getDefaultInstance())
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
         AbstractJay.executorPool.submit {
             val timer = System.currentTimeMillis()
             try {
                 val pingData = blockingStub
                         .withDeadlineAfter(timeout, TimeUnit.MILLISECONDS)
                         .ping(JayProto.Ping.newBuilder().setData(ByteString.copyFrom(ByteArray(payload))).setReply(reply).build())
                 JayLogger.logInfo("SEND_PING", actions = *arrayOf("DATA_SIZE=${pingData.data.size()}"))
                 callback?.invoke((System.currentTimeMillis() - timer).toInt())
             } catch (e: TimeoutException) {
                 JayLogger.logError("TIMEOUT")
                 callback?.invoke(-1)
             }
         }
     }

    fun updateWorkers(callback: (() -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.updateWorkers(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                call.get(); callback()
            } catch (e: Exception) {
                callback()
            }
        }, AbstractJay.executorPool)
    }

    fun selectModel(model: Model, callback: ((JayProto.Status) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.setModel(model.getProto())
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractJay.executorPool)
    }

    fun getModels(callback: ((Set<Model>) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.getModels(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback?.invoke(JayUtils.parseModels(call.get()))
            } catch (e: ExecutionException) {
                JayLogger.logError("UNAVAILABLE")
            }
        }, AbstractJay.executorPool)
    }

    fun getSchedulers(callback: ((Set<Pair<String, String>>) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.getSchedulers(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback?.invoke(JayUtils.parseSchedulers(call.get()))
            } catch (e: ExecutionException) {
                JayLogger.logError("UNAVAILABLE")
            }
        }, AbstractJay.executorPool)
    }

    fun setScheduler(id: String, callback: ((Boolean) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.setScheduler(JayProto.Scheduler.newBuilder().setId(id).build())
        call.addListener(Runnable {
            try {
                callback(call.get().code == JayProto.StatusCode.Success)
            } catch (e: ExecutionException) {
                callback(false)
            }
        }, AbstractJay.executorPool)
    }

    fun advertiseWorkerStatus(request: JayProto.Worker?, completeCallback: () -> Unit) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.advertiseWorkerStatus(request)
        call.addListener(Runnable { completeCallback() }, AbstractJay.executorPool)
    }

    fun diffuseWorkerStatus(request: JayProto.Worker?) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.diffuseWorkerStatus(request)
        call.addListener(Runnable {
            try {
                JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}"))
            } catch (e: ExecutionException) {
            }
        }, AbstractJay.executorPool)
    }

    fun requestWorkerStatus(callback: ((JayProto.Worker?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.requestWorkerStatus(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback(call.get())
            } catch (e: ExecutionException) {
                callback(null)
            }
        }, AbstractJay.executorPool)
    }


    fun listenMulticastWorkers(stopListener: Boolean = false, callback: ((JayProto.Status) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.listenMulticast(BoolValue.of(stopListener))
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractJay.executorPool)
    }

    /*fun announceMulticast() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.announceMulticast(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}"))
            } catch (e: ExecutionException) {
                JayLogger.logError("ERROR")
            }}, AbstractJay.executorPool)
    }*/

    fun enableHearBeats(workerTypes: JayProto.WorkerTypes, callback: ((JayProto.Status) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.enableHearBeats(workerTypes)
        call.addListener(Runnable { JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")); callback(call.get()) }, AbstractJay.executorPool)
    }

    fun enableBandwidthEstimates(bandwidthEstimateConfig: JayProto.BandwidthEstimate, callback: ((JayProto.Status) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.enableBandwidthEstimates(bandwidthEstimateConfig)
        call.addListener(Runnable { JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")); callback(call.get()) }, AbstractJay.executorPool)
    }

    fun disableHearBeats() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.disableHearBeats(Empty.getDefaultInstance())
        call.addListener(Runnable { JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")) }, AbstractJay.executorPool)
    }

    fun disableBandwidthEstimates() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.disableBandwidthEstimates(Empty.getDefaultInstance())
        call.addListener(Runnable { JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")) }, AbstractJay.executorPool)
    }

    fun updateSmartSchedulerWeights(computeTime: Float, queueSize: Float, runningJobs: Float, battery: Float,
                                    bandwidth: Float, callback: ((JayProto.Status?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            callback(JayUtils.genStatusError())
        }
        val call = futureStub.updateSmartSchedulerWeights(JayProto.Weights.newBuilder().setComputeTime(computeTime)
                .setQueueSize(queueSize).setRunningJobs(runningJobs).setBattery(battery).setBandwidth(bandwidth)
                .build())
        call.addListener(Runnable {
            try {
                callback(call.get())
            } catch (e: RuntimeException) {
                callback(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }

    fun announceServiceStatus(serviceStatus: JayProto.ServiceStatus, callback: ((JayProto.Status?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            callback(JayUtils.genStatusError())
        }
        try {
            val call = futureStub.announceServiceStatus(serviceStatus)
            call.addListener(Runnable {
                try {
                    callback.invoke(call.get())
                } catch (e: Exception) {
                }
            }, AbstractJay.executorPool)
        } catch (e: StatusRuntimeException) {
        }
    }

    fun stopService(callback: (JayProto.Status?) -> Unit) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) callback(JayUtils.genStatusError())
        val call = futureStub.stopService(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback(call.get())
            } catch (e: Exception) {
                callback(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }
}