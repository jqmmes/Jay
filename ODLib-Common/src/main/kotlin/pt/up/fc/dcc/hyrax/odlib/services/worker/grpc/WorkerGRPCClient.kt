package pt.up.fc.dcc.hyrax.odlib.services.worker.grpc

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.WorkerGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

class WorkerGRPCClient(host: String) : GRPCClientBase<WorkerGrpc.WorkerBlockingStub, WorkerGrpc.WorkerFutureStub>
(host, ODSettings.brokerPort) {
    override var blockingStub: WorkerGrpc.WorkerBlockingStub = WorkerGrpc.newBlockingStub(channel)
    override var futureStub: WorkerGrpc.WorkerFutureStub = WorkerGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = WorkerGrpc.newBlockingStub(channel)
        futureStub = WorkerGrpc.newFutureStub(channel)
    }

    fun submitJob(job: ODProto.Job, callback: (ODProto.JobResults) -> Unit) {
        val futureJob = futureStub.submitJob(job)
        futureJob.addListener({ callback(futureJob.get()) }, { J -> threadPool.submit(J) })
    }
}