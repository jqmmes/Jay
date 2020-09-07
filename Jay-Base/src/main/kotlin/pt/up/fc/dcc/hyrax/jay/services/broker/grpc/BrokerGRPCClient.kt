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
import pt.up.fc.dcc.hyrax.jay.proto.BrokerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayState
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayThreadPoolExecutor
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

/**
 * https://github.com/grpc/grpc/blob/master/doc/connectivity-semantics-and-api.md
 * TRANSIENT_FAILURE
 */

@Suppress("DuplicatedCode", "unused")
class BrokerGRPCClient(host: String) : GRPCClientBase<BrokerServiceGrpc.BrokerServiceBlockingStub, BrokerServiceGrpc.BrokerServiceFutureStub>
(host, JaySettings.BROKER_PORT) {
    override var blockingStub: BrokerServiceGrpc.BrokerServiceBlockingStub = BrokerServiceGrpc.newBlockingStub(channel)
    override var futureStub: BrokerServiceGrpc.BrokerServiceFutureStub = BrokerServiceGrpc.newFutureStub(channel)
    private var asyncStub: BrokerServiceGrpc.BrokerServiceStub = BrokerServiceGrpc.newStub(channel)
    private val executePool: JayThreadPoolExecutor = JayThreadPoolExecutor(10)
    private val pingPool: JayThreadPoolExecutor = JayThreadPoolExecutor(10)

    fun setNewPort(port: Int) {
        this.reconnectChannel(port = port)
        this.port = port
    }

    override fun reconnectStubs() {
        blockingStub = BrokerServiceGrpc.newBlockingStub(channel)
        futureStub = BrokerServiceGrpc.newFutureStub(channel)
    }

    fun scheduleTask(task: Task, callback: ((JayProto.Response) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.scheduleTask(task.getProto())
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractJay.executorPool)
    }

    class ResponseStreamObserver(val task: JayProto.Task?, val local: Boolean = false,
                                 private val startTime: Long, val sendCb: (() -> Unit),
                                 val dataReachedWorkerCb: (() -> Unit), val endCb: (() -> Unit),
                                 val callback: ((JayProto.Response) -> Unit)? = null,
                                 private val schedulerInformCallback: (() -> Unit)? = null) : StreamObserver<JayProto.Response> {
        private var lastResult: JayProto.Response? = null

        override fun onNext(results: JayProto.Response) {
            JayLogger.logInfo("RECEIVED_RESPONSE", task?.id ?: "", results.status.code.name)
            lastResult = results
            when (results.status.code) {
                JayProto.StatusCode.Ready -> sendCb()
                JayProto.StatusCode.Received -> {
                    dataReachedWorkerCb()
                    if (JaySettings.BANDWIDTH_ESTIMATE_TYPE in arrayOf("PASSIVE", "ALL")) {
                        BrokerService.passiveBandwidthUpdate(task?.id ?: "", task?.toByteArray()?.size
                                ?: -1, System.currentTimeMillis() - startTime)
                    }
                    if (!local) BrokerService.profiler.unSetState(JayState.DATA_SND)
                    JayLogger.logInfo("DATA_REACHED_SERVER", task?.id ?: "",
                            "DATA_SIZE=${task?.toByteArray()?.size}",
                            "DURATION_MILLIS=${startTime - System.currentTimeMillis()}",
                            "BATTERY_CHARGE=${BrokerService.powerMonitor?.getCharge()}",
                            "BATTERY_CURRENT=${BrokerService.powerMonitor?.getCurrentNow()}",
                            "BATTERY_REMAINING_ENERGY=${BrokerService.powerMonitor?.getRemainingEnergy()}")
                }
                JayProto.StatusCode.Success -> {
                    JayLogger.logInfo("EXECUTION_COMPLETE", task?.id ?: "",
                            "DATA_SIZE=${task?.toByteArray()?.size}",
                            "DURATION_MILLIS=${startTime - System.currentTimeMillis()}",
                            "BATTERY_CHARGE=${BrokerService.powerMonitor?.getCharge()}",
                            "BATTERY_CURRENT=${BrokerService.powerMonitor?.getCurrentNow()}",
                            "BATTERY_REMAINING_ENERGY=${BrokerService.powerMonitor?.getRemainingEnergy()}")
                    callback?.invoke(lastResult ?: JayProto.Response.getDefaultInstance())
                    schedulerInformCallback?.invoke()
                    endCb()
                }
                JayProto.StatusCode.End -> JayLogger.logInfo("END_TRANSFER", task?.id ?: "")
                else -> onError(Throwable("Error Received onNext for taskId: ${task?.id}"))
            }
        }

        override fun onError(t: Throwable) {
            JayLogger.logError("ERROR", task?.id ?: "")
            callback?.invoke(JayProto.Response.getDefaultInstance()); JayLogger.logError("ERROR", task?.id ?: "",
                    "BATTERY_CHARGE=${BrokerService.powerMonitor?.getCharge()}",
                    "BATTERY_CURRENT=${BrokerService.powerMonitor?.getCurrentNow()}",
                    "BATTERY_REMAINING_ENERGY=${BrokerService.powerMonitor?.getRemainingEnergy()}")
            t.printStackTrace()
        }

        override fun onCompleted() {
            JayLogger.logInfo("COMPLETE", task?.id ?: "",
                    "DURATION_MILLIS=${startTime - System.currentTimeMillis()}",
                    "BATTERY_CHARGE=${BrokerService.powerMonitor?.getCharge()}",
                    "BATTERY_CURRENT=${BrokerService.powerMonitor?.getCurrentNow()}",
                    "BATTERY_REMAINING_ENERGY=${BrokerService.powerMonitor?.getRemainingEnergy()}")
        }
    }

    fun executeTask(task: JayProto.Task?, local: Boolean = false, callback: ((JayProto.Response) -> Unit)? = null,
                    schedulerInformCallback: (() -> Unit)? = null) {
        if (JaySettings.TRANSFER_BASELINE_FLAG) {
            thread {
                BrokerService.benchmarkNetwork(JaySettings.TRANSFER_BASELINE_DURATION, this, task, callback,
                        schedulerInformCallback)
            }
            return
        }
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val taskId = task?.id ?: ""
        executePool.submit {
            val startTime = System.currentTimeMillis()
            JayLogger.logInfo("INIT", taskId,
                    "BATTERY_CHARGE=${BrokerService.powerMonitor?.getCharge()}",
                    "BATTERY_CURRENT=${BrokerService.powerMonitor?.getCurrentNow()}",
                    "BATTERY_REMAINING_ENERGY=${BrokerService.powerMonitor?.getRemainingEnergy()}")
            var taskStreamObserver: StreamObserver<JayProto.Task>? = null

            taskStreamObserver = asyncStub.executeTask(ResponseStreamObserver(
                    task, local, startTime,
                    sendCb = {
                        if (!local) BrokerService.profiler.setState(JayState.DATA_SND)
                        taskStreamObserver?.onNext(
                                JayProto.Task.newBuilder(task)
                                        .setStatus(JayProto.Task.Status.TRANSFER)
                                        .build())
                    },
                    dataReachedWorkerCb = {
                        taskStreamObserver?.onNext(
                                JayProto.Task.newBuilder()
                                        .setStatus(JayProto.Task.Status.END_TRANSFER)
                                        .build())
                        taskStreamObserver?.onCompleted()
                    },
                    endCb = { JayLogger.logInfo("EXECUTE_TASK_COMPLETE", taskId) },
                    callback = callback,
                    schedulerInformCallback = schedulerInformCallback)
            )
            taskStreamObserver.onNext(
                    JayProto.Task.newBuilder()
                            .setStatus(JayProto.Task.Status.BEGIN_TRANSFER)
                            .setLocalTask(local)
                            .build())
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
         pingPool.submit {
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
             } catch (e: StatusRuntimeException) {
                 JayLogger.logError("TIMEOUT")
                 callback?.invoke(-1)
             }
         }
     }

    fun notifySchedulerForAvailableWorkers(callback: (() -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.notifySchedulerForAvailableWorkers(Empty.getDefaultInstance())
        call.addListener({
            try {
                call.get(); callback()
            } catch (e: Exception) {
                callback()
            }
        }, { R -> R.run() })
    }

    fun callExecutorAction(request: JayProto.Request?, callback: ((JayProto.Response?) -> Unit)?) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        JayLogger.logInfo("INIT", actions = *arrayOf("ACTION=${request?.request}"))
        val call = futureStub.callExecutorAction(request)
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE", actions = *arrayOf("ACTION=${request?.request}"))
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR", actions = *arrayOf("ACTION=${request?.request}"))
                callback?.invoke(JayProto.Response.newBuilder().setStatus(JayUtils.genStatusError()).build())
            }
        }, AbstractJay.executorPool)
    }

    fun runExecutorAction(request: JayProto.Request?, callback: ((JayProto.Status?) -> Unit)?) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        JayLogger.logInfo("INIT", actions = *arrayOf("ACTION=${request?.request}"))
        val call = futureStub.runExecutorAction(request)
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE", actions = *arrayOf("ACTION=${request?.request}"))
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR", actions = *arrayOf("ACTION=${request?.request}"))
                callback?.invoke(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }

    fun listTaskExecutors(request: Empty?, callback: ((JayProto.TaskExecutors) -> Unit)?) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        JayLogger.logInfo("INIT")
        val call = futureStub.listTaskExecutors(request)
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE")
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR")
                callback?.invoke(JayProto.TaskExecutors.getDefaultInstance())
            }
        }, AbstractJay.executorPool)
    }

    fun selectTaskExecutor(request: JayProto.TaskExecutor?, callback: ((JayProto.Status?) -> Unit)?) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        JayLogger.logInfo("INIT", actions = *arrayOf("ACTION=${request?.id}"))
        val call = futureStub.selectTaskExecutor(request)
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE", actions = *arrayOf("ACTION=${request?.id}"))
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR", actions = *arrayOf("ACTION=${request?.id}"))
                callback?.invoke(JayUtils.genStatusError())
            }
        }, AbstractJay.executorPool)
    }

    fun setExecutorSettings(request: JayProto.Settings?, callback: ((JayProto.Status?) -> Unit)?) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        JayLogger.logInfo("INIT", actions = *arrayOf("SETTINGS=${request?.settingMap?.keys}"))
        val call = futureStub.setExecutorSettings(request)
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE", actions = *arrayOf("SETTINGS=${request?.settingMap?.keys}"))
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR", actions = *arrayOf("SETTINGS=${request?.settingMap?.keys}"))
                callback?.invoke(JayUtils.genStatusError())
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

    fun setSchedulerSettings(request: JayProto.Settings?, callback: ((JayProto.Status?) -> Unit)?) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        JayLogger.logInfo("INIT", actions = *arrayOf("SETTINGS=${request?.settingMap?.keys}"))
        val call = futureStub.setSchedulerSettings(request)
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
                JayLogger.logInfo("COMPLETE", actions = *arrayOf("SETTINGS=${request?.settingMap?.keys}"))
            } catch (e: ExecutionException) {
                JayLogger.logInfo("ERROR", actions = *arrayOf("SETTINGS=${request?.settingMap?.keys}"))
                callback?.invoke(JayUtils.genStatusError())
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

    fun enableHeartBeats(workerTypes: JayProto.WorkerTypes, callback: ((JayProto.Status) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.enableHearBeats(workerTypes)
        call.addListener(Runnable { JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")); callback(call.get()) }, AbstractJay.executorPool)
    }

    fun enableBandwidthEstimates(bandwidthEstimateConfig: JayProto.BandwidthEstimate, callback: ((JayProto.Status) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.enableBandwidthEstimates(bandwidthEstimateConfig)
        call.addListener(Runnable { JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")); callback(call.get()) }, AbstractJay.executorPool)
    }

    fun disableHeartBeats() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.disableHearBeats(Empty.getDefaultInstance())
        call.addListener(Runnable { JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")) }, AbstractJay.executorPool)
    }

    fun disableBandwidthEstimates() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.disableBandwidthEstimates(Empty.getDefaultInstance())
        call.addListener(Runnable { JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")) }, AbstractJay.executorPool)
    }

    fun enableWorkerStatusAdvertisement(callback: ((JayProto.Status) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.enableWorkerStatusAdvertisement(Empty.getDefaultInstance())
        call.addListener(Runnable { JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")); callback(call.get()) }, AbstractJay.executorPool)
    }

    fun disableWorkerStatusAdvertisement() {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.disableWorkerStatusAdvertisement(Empty.getDefaultInstance())
        call.addListener(Runnable { JayLogger.logInfo("COMPLETE", actions = *arrayOf("STATUS_CODE=${call.get().code.name}")) }, AbstractJay.executorPool)
    }


    fun getExpectedCurrent(callback: ((JayProto.CurrentEstimations?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.getExpectedCurrent(Empty.getDefaultInstance())
        call.addListener(Runnable { callback.invoke(call.get()); JayLogger.logInfo("COMPLETE") }, AbstractJay.executorPool)
    }

    fun getExpectedCurrentFromRemote(worker: JayProto.Worker?, callback: ((JayProto.CurrentEstimations?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.getExpectedCurrentFromRemote(worker)
        call.addListener(Runnable { callback.invoke(call.get()); JayLogger.logInfo("COMPLETE") }, AbstractJay.executorPool)
    }

    fun getExpectedPower(callback: ((JayProto.PowerEstimations?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.getExpectedPower(Empty.getDefaultInstance())
        call.addListener(Runnable { callback.invoke(call.get()); JayLogger.logInfo("COMPLETE") }, AbstractJay.executorPool)
    }

    fun getExpectedPowerFromRemote(worker: JayProto.Worker?, callback: ((JayProto.PowerEstimations?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.getExpectedPowerFromRemote(worker)
        call.addListener(Runnable { callback.invoke(call.get()); JayLogger.logInfo("COMPLETE") }, AbstractJay.executorPool)
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