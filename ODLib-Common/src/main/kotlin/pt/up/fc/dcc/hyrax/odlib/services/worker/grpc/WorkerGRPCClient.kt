package pt.up.fc.dcc.hyrax.odlib.services.worker.grpc

import com.google.protobuf.Empty
import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

class WorkerGRPCClient(host: String) : GRPCClientBase<WorkerServiceGrpc.WorkerServiceBlockingStub, WorkerServiceGrpc.WorkerServiceFutureStub>
(host, ODSettings.workerPort) {
    override var blockingStub: WorkerServiceGrpc.WorkerServiceBlockingStub = WorkerServiceGrpc.newBlockingStub(channel)
    override var futureStub: WorkerServiceGrpc.WorkerServiceFutureStub = WorkerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = WorkerServiceGrpc.newBlockingStub(channel)
        futureStub = WorkerServiceGrpc.newFutureStub(channel)
    }

    fun execute(job: ODProto.Job?, callback: ((ODProto.Results?) -> Unit)? = null) {
        val futureJob = futureStub.execute(job)
        futureJob.addListener(Runnable{ callback?.invoke(futureJob.get()) }, AbstractODLib.executorPool)
    }

    fun listModels(callback: ((ODProto.Models) -> Unit)? = null) {
        val call = futureStub.listModels(Empty.getDefaultInstance())
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }

    fun selectModel(request: ODProto.Model?, callback: ((ODProto.Status?) -> Unit)?) {
        val call = futureStub.selectModel(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }


}