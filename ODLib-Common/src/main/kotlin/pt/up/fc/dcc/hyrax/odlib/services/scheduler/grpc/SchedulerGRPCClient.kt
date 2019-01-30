package pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.SchedulerGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

class SchedulerGRPCClient(host: String) : GRPCClientBase<SchedulerGrpc.SchedulerBlockingStub, SchedulerGrpc.SchedulerFutureStub>
(host, ODSettings.brokerPort) {
    override var blockingStub: SchedulerGrpc.SchedulerBlockingStub = SchedulerGrpc.newBlockingStub(channel)
    override var futureStub: SchedulerGrpc.SchedulerFutureStub = SchedulerGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = SchedulerGrpc.newBlockingStub(channel)
        futureStub = SchedulerGrpc.newFutureStub(channel)
    }

    fun scheduleJob(job: ODProto.Job, callback: (R: ODProto.RemoteClient) -> Unit) {
        val futureJob = futureStub.scheduleJob(job)
        futureJob.addListener({ callback(futureJob.get()) }, { J -> threadPool.submit(J) })
    }
}