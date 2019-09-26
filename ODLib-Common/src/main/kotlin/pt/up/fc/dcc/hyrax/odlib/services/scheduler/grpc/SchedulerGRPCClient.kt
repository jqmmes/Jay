package pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc

import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.SchedulerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.ExecutionException

@Suppress("DuplicatedCode")
class SchedulerGRPCClient(host: String) : GRPCClientBase<SchedulerServiceGrpc.SchedulerServiceBlockingStub, SchedulerServiceGrpc.SchedulerServiceFutureStub>
(host, ODSettings.schedulerPort) {
    override var blockingStub: SchedulerServiceGrpc.SchedulerServiceBlockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
    override var futureStub: SchedulerServiceGrpc.SchedulerServiceFutureStub = SchedulerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
        futureStub = SchedulerServiceGrpc.newFutureStub(channel)
    }

    fun schedule(request: ODProto.Job?, callback: ((ODProto.Worker?) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.schedule(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun listSchedulers(callback: ((ODProto.Schedulers?) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.listSchedulers(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
            } catch (e: ExecutionException) { ODLogger.logError("UNAVAILABLE") }
        }, AbstractODLib.executorPool)
    }

    fun setScheduler(request: ODProto.Scheduler?, callback: ((ODProto.Status?) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        val call = futureStub.setScheduler(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun notifyWorkerUpdate(request: ODProto.Worker?, callback: ((ODProto.Status?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            return callback(ODUtils.genStatus(ODProto.StatusCode.Error))
        }
        val call = futureStub.notifyWorkerUpdate(request)
        call.addListener(Runnable {
            try { callback(call.get()) }
            catch (e: ExecutionException) { callback(ODUtils.genStatus(ODProto.StatusCode.Error)) }
        }, AbstractODLib.executorPool)
    }

    fun notifyWorkerFailure(request: ODProto.Worker?, callback: ((ODProto.Status?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            return callback(ODUtils.genStatus(ODProto.StatusCode.Error))
        }
        val call = futureStub.notifyWorkerFailure(request)
        call.addListener(Runnable {
            try {callback(call.get()) }
            catch (e: ExecutionException) { callback(ODUtils.genStatus(ODProto.StatusCode.Error)) }
        }, AbstractODLib.executorPool)
    }

    fun updateSmartSchedulerWeights(weights: ODProto.Weights?, callback: ((ODProto.Status?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            callback(ODUtils.genStatus(ODProto.StatusCode.Error))
        }
        val call = futureStub.updateSmartSchedulerWeights(weights)
        call.addListener(Runnable { callback(call.get()) }, AbstractODLib.executorPool)
    }

    fun testService(serviceStatus: ((ODProto.ServiceStatus?) -> Unit)) {
        if (channel.getState(true) != ConnectivityState.READY) serviceStatus(null)
        val call = futureStub.testService(Empty.getDefaultInstance())
        call.addListener(Runnable { try {serviceStatus(call.get())} catch (e: Exception) {serviceStatus(null)}}, AbstractODLib.executorPool)
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