package pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc

import com.google.protobuf.Empty
import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.SchedulerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

class SchedulerGRPCClient(host: String) : GRPCClientBase<SchedulerServiceGrpc.SchedulerServiceBlockingStub, SchedulerServiceGrpc.SchedulerServiceFutureStub>
(host, ODSettings.schedulerPort) {
    override var blockingStub: SchedulerServiceGrpc.SchedulerServiceBlockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
    override var futureStub: SchedulerServiceGrpc.SchedulerServiceFutureStub = SchedulerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = SchedulerServiceGrpc.newBlockingStub(channel)
        futureStub = SchedulerServiceGrpc.newFutureStub(channel)
    }

    fun schedule(request: ODProto.Job?, callback: ((ODProto.Worker?) -> Unit)? = null) {
        val call = futureStub.schedule(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun listSchedulers(callback: ((ODProto.Schedulers?) -> Unit)? = null) {
        val call = futureStub.listSchedulers(Empty.getDefaultInstance())
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun setScheduler(request: ODProto.Scheduler?, callback: ((ODProto.Status?) -> Unit)? = null) {
        val call = futureStub.setScheduler(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun notify(request: ODProto.Worker?, callback: ((ODProto.Status?) -> Unit)? = null) {
        val call = futureStub.notify(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }
}