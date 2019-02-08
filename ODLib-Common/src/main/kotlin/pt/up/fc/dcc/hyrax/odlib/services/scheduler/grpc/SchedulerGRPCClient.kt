package pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.SchedulerGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SchedulerGRPCClient(host: String) : GRPCClientBase<SchedulerGrpc.SchedulerBlockingStub, SchedulerGrpc.SchedulerFutureStub>
(host, ODSettings.schedulerPort) {
    override val threadPool: ExecutorService = Executors.newSingleThreadExecutor()!!
    override var blockingStub: SchedulerGrpc.SchedulerBlockingStub = SchedulerGrpc.newBlockingStub(channel)
    override var futureStub: SchedulerGrpc.SchedulerFutureStub = SchedulerGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = SchedulerGrpc.newBlockingStub(channel)
        futureStub = SchedulerGrpc.newFutureStub(channel)
    }

    fun schedule(job: ODProto.Job, callback: (R: ODProto.WorkerId) -> Unit) {
        println("SchedulerGRPCClient schedulerJob")
        val futureJob = futureStub.schedule(job)
        futureJob.addListener({ callback(futureJob.get()) }, { J -> threadPool.submit(J) })
    }

    fun notify(request: ODProto.WorkerStatus?, callback: ((R: ODProto.RequestStatus) -> Unit)? = null) {
        val futureJob = futureStub.notify(request)
        futureJob.addListener({ if (callback != null) callback(ODProto.RequestStatus.newBuilder().setCodeValue(0).build()) }, { J -> threadPool.submit(J) })
    }
}