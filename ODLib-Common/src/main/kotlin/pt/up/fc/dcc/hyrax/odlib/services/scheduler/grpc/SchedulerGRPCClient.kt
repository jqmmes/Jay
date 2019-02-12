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

    fun schedule(job: ODProto.Job, callback: (ODProto.WorkerId) -> Unit) {
        println("SchedulerGRPCClient scheduleJob")
        val call = futureStub.schedule(job)
        call.addListener({ callback(call.get()); println("Scheduled") }, { J -> threadPool.submit(J) })
    }

    fun notify(request: ODProto.WorkerStatus?, callback: ((ODProto.RequestStatus) -> Unit)? = null) {
        val futureJob = futureStub.notify(request)
        futureJob.addListener({if (callback != null) callback(futureJob.get()); println("notified ${futureJob.get().code}") }, { J -> threadPool.submit(J) })
    }
}