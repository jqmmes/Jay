package pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc

import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import pt.up.fc.dcc.hyrax.jay.AbstractJay
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.SchedulerServiceGrpc
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.ExecutionException

@Suppress("DuplicatedCode")
class SchedulerGRPCClient(host: String) : GRPCClientBase<SchedulerServiceGrpc.SchedulerServiceBlockingStub, SchedulerServiceGrpc.SchedulerServiceFutureStub>
(host, JaySettings.SCHEDULER_PORT) {
    override var blockingStub: SchedulerServiceGrpc.SchedulerServiceBlockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
    override var futureStub: SchedulerServiceGrpc.SchedulerServiceFutureStub = SchedulerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
        futureStub = SchedulerServiceGrpc.newFutureStub(channel)
    }

    fun schedule(request: JayProto.JobDetails?, callback: ((JayProto.Worker?) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.schedule(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractJay.executorPool)
    }

    fun notifyJobComplete(request: JayProto.JobDetails?) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            return
        }
        AbstractJay.executorPool.submit { blockingStub.notifyJobComplete(request) }
    }

    fun listSchedulers(callback: ((JayProto.Schedulers?) -> Unit)? = null) {
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
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.setScheduler(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractJay.executorPool)
    }

    fun notifyWorkerUpdate(request: JayProto.Worker?, callback: ((JayProto.Status?) -> Unit)) {
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

    fun updateSmartSchedulerWeights(weights: JayProto.Weights?, callback: ((JayProto.Status?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            callback(JayUtils.genStatus(JayProto.StatusCode.Error))
        }
        val call = futureStub.updateSmartSchedulerWeights(weights)
        call.addListener(Runnable { callback(call.get()) }, AbstractJay.executorPool)
    }

    fun testService(serviceStatus: ((JayProto.ServiceStatus?) -> Unit)) {
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