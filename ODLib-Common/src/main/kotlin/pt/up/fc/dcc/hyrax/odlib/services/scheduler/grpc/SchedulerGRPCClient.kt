package pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc

import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import io.grpc.StatusRuntimeException
import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.SchedulerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.ExecutionException

class SchedulerGRPCClient(host: String) : GRPCClientBase<SchedulerServiceGrpc.SchedulerServiceBlockingStub, SchedulerServiceGrpc.SchedulerServiceFutureStub>
(host, ODSettings.schedulerPort) {
    override var blockingStub: SchedulerServiceGrpc.SchedulerServiceBlockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
    override var futureStub: SchedulerServiceGrpc.SchedulerServiceFutureStub = SchedulerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        println("SchedulerGRPCClient::reconnectStubs")
        blockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
        futureStub = SchedulerServiceGrpc.newFutureStub(channel)
    }

    fun schedule(request: ODProto.Job?, callback: ((ODProto.Worker?) -> Unit)? = null) {
        println("SchedulerGRPCClient::schedule ${channel.getState(false)}")
        val call = futureStub.schedule(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun listSchedulers(callback: ((ODProto.Schedulers?) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        println("SchedulerGRPCClient::listSchedulers ${channel.getState(false)}")
        val call = futureStub.listSchedulers(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                println("${call.isDone}\t${call.isCancelled}")
                callback?.invoke(call.get())
            } catch (e: ExecutionException) {
                println("listSchedulers Unavailable")
            }
        }, AbstractODLib.executorPool)
    }

    fun setScheduler(request: ODProto.Scheduler?, callback: ((ODProto.Status?) -> Unit)? = null) {
        println("SchedulerGRPCClient::setScheduler ${channel.getState(false)}")
        val call = futureStub.setScheduler(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun notify(request: ODProto.Worker?, callback: ((ODProto.Status?) -> Unit)) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) {
            channel.resetConnectBackoff()
            callback(ODUtils.genStatus(ODProto.StatusCode.Error))
        }
        println("SchedulerGRPCClient::notify ${channel.getState(false)}")
        val call = futureStub.notify(request)
        call.addListener(Runnable {
            try { callback(call.get()) }
            catch (e: ExecutionException) { callback(ODUtils.genStatus(ODProto.StatusCode.Error)) }
        }, AbstractODLib.executorPool)
    }
}