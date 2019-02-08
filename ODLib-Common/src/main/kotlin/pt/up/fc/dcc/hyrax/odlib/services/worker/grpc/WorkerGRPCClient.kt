package pt.up.fc.dcc.hyrax.odlib.services.worker.grpc

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.WorkerGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class WorkerGRPCClient(host: String) : GRPCClientBase<WorkerGrpc.WorkerBlockingStub, WorkerGrpc.WorkerFutureStub>
(host, ODSettings.workerPort) {
    override val threadPool: ExecutorService = Executors.newSingleThreadExecutor()!!
    override var blockingStub: WorkerGrpc.WorkerBlockingStub = WorkerGrpc.newBlockingStub(channel)
    override var futureStub: WorkerGrpc.WorkerFutureStub = WorkerGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = WorkerGrpc.newBlockingStub(channel)
        futureStub = WorkerGrpc.newFutureStub(channel)
    }

    fun execute(job: ODProto.Job?, callback: ((ODProto.JobResults) -> Unit)? = null) {
        println("WorkerGRPCClient queueJob")
        val futureJob = futureStub.execute(job)
        futureJob.addListener({ if (callback != null) callback(futureJob.get()) }, { J -> threadPool.submit(J) })
    }
}