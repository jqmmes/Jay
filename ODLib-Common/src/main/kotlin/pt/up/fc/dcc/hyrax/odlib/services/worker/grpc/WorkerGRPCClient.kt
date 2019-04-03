package pt.up.fc.dcc.hyrax.odlib.services.worker.grpc

import com.google.protobuf.Empty
import io.grpc.ConnectivityState
import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.WorkerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.concurrent.ExecutionException

class WorkerGRPCClient(host: String) : GRPCClientBase<WorkerServiceGrpc.WorkerServiceBlockingStub, WorkerServiceGrpc.WorkerServiceFutureStub>
(host, ODSettings.workerPort) {
    override var blockingStub: WorkerServiceGrpc.WorkerServiceBlockingStub = WorkerServiceGrpc.newBlockingStub(channel)
    override var futureStub: WorkerServiceGrpc.WorkerServiceFutureStub = WorkerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        println("WorkerGRPCClient::reconnectStubs")
        blockingStub = WorkerServiceGrpc.newBlockingStub(channel)
        futureStub = WorkerServiceGrpc.newFutureStub(channel)
    }

    fun execute(job: ODProto.Job?, callback: ((ODProto.Results?) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        println("WorkerGRPCClient::execute")
        val futureJob = futureStub.execute(job)
        futureJob.addListener(Runnable{ callback?.invoke(futureJob.get()) }, AbstractODLib.executorPool)
    }

    fun listModels(callback: ((ODProto.Models) -> Unit)? = null) {
        if (channel.getState(true) == ConnectivityState.TRANSIENT_FAILURE) channel.resetConnectBackoff()
        println("WorkerGRPCClient::listModels")
        val call = futureStub.listModels(Empty.getDefaultInstance())
        call.addListener(Runnable {
            try {
                callback?.invoke(call.get())
            } catch (e: ExecutionException) {
                println("listModels Unavailable")
            }

        }, AbstractODLib.executorPool)
    }

    fun selectModel(request: ODProto.Model?, callback: ((ODProto.Status?) -> Unit)?) {
        println("WorkerGRPCClient::selectModel")
        val call = futureStub.selectModel(request)
        call.addListener(Runnable { callback?.invoke(call.get()) }, AbstractODLib.executorPool)
    }
}