package pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc

import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import pt.up.fc.dcc.hyrax.jay.AbstractJay
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.SchedulerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayThreadPoolExecutor
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.ExecutionException

@Suppress("DuplicatedCode")
class SchedulerGRPCClient(private val host: String) : GRPCClientBase<SchedulerServiceGrpc.SchedulerServiceBlockingStub,
        SchedulerServiceGrpc.SchedulerServiceFutureStub>(host, JaySettings.SCHEDULER_PORT) {
    override var blockingStub: SchedulerServiceGrpc.SchedulerServiceBlockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
    override var futureStub: SchedulerServiceGrpc.SchedulerServiceFutureStub = SchedulerServiceGrpc.newFutureStub(channel)
    private val notifyPool: JayThreadPoolExecutor = JayThreadPoolExecutor(10)

    private fun checkConnection() {
        if (this.port != JaySettings.SCHEDULER_PORT) {
            reconnectChannel(host, JaySettings.SCHEDULER_PORT)
        }
    }

    override fun reconnectStubs() {
        blockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
        futureStub = SchedulerServiceGrpc.newFutureStub(channel)
    }

    fun schedule(request: JayProto.TaskDetails?, callback: ((JayProto.Worker?) -> Unit)? = null) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.schedule(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractJay.executorPool)
    }

    fun notifyTaskComplete(request: JayProto.TaskDetails?) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            return
        }
        notifyPool.submit { blockingStub.notifyTaskComplete(request) }
    }

    fun listSchedulers(callback: ((JayProto.Schedulers?) -> Unit)? = null) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.listSchedulers(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
            } catch (e: ExecutionException) {
                JayLogger.logError("UNAVAILABLE")
            }
        }, AbstractJay.executorPool)
    }

    fun setScheduler(request: JayProto.Scheduler?, callback: ((JayProto.Status?) -> Unit)? = null) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.setScheduler(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractJay.executorPool)
    }

    fun setSchedulerSettings(request: JayProto.Settings?, callback: ((JayProto.Status?) -> Unit)?) {
        checkConnection()
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

    fun notifyWorkerUpdate(request: JayProto.Worker?, callback: ((JayProto.Status?) -> Unit)) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            return callback(JayUtils.genStatus(JayProto.StatusCode.Error))
        }
        val call = futureStub.notifyWorkerUpdate(request)
        call.addListener(Runnable {
            try {
                callback(call.get())
            } catch (e: ExecutionException) {
                callback(JayUtils.genStatus(JayProto.StatusCode.Error))
            }
        }, AbstractJay.executorPool)
    }

    fun notifyWorkerFailure(request: JayProto.Worker?, callback: ((JayProto.Status?) -> Unit)) {
        checkConnection()
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            return callback(JayUtils.genStatus(JayProto.StatusCode.Error))
        }
        val call = futureStub.notifyWorkerFailure(request)
        call.addListener(Runnable {
            try {
                callback(call.get())
            } catch (e: ExecutionException) {
                callback(JayUtils.genStatus(JayProto.StatusCode.Error))
            }
        }, AbstractJay.executorPool)
    }

    fun testService(serviceStatus: ((JayProto.ServiceStatus?) -> Unit)) {
        checkConnection()
        if (channel.getState(true) != ConnectivityState.READY) serviceStatus(null)
        val call = futureStub.testService(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                serviceStatus(call.get())
            } catch (e: Exception) {
                serviceStatus(null)
            }
        }, AbstractJay.executorPool)
    }

    fun stopService(callback: (JayProto.Status?) -> Unit) {
        checkConnection()
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