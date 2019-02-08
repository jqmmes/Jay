package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import com.google.common.util.concurrent.ListenableFuture
import com.google.protobuf.ByteString
import com.sun.org.apache.xpath.internal.operations.Bool
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BrokerGRPCClient(host: String) : GRPCClientBase<BrokerGrpc.BrokerBlockingStub, BrokerGrpc.BrokerFutureStub>
(host, ODSettings.brokerPort) {
    override val threadPool: ExecutorService = Executors.newSingleThreadExecutor()!!
    override var blockingStub: BrokerGrpc.BrokerBlockingStub = BrokerGrpc.newBlockingStub(channel)
    override var futureStub: BrokerGrpc.BrokerFutureStub = BrokerGrpc.newFutureStub(channel)
    private val pingExecutor: ExecutorService = Executors.newSingleThreadExecutor()!!

    override fun reconnectStubs() {
        blockingStub = BrokerGrpc.newBlockingStub(channel)
        futureStub = BrokerGrpc.newFutureStub(channel)
    }

    fun scheduleJob(job: ODJob) {
        futureStub.scheduleJob(ODUtils.genJobRequest(job))
    }

    fun executeJob(job: ODProto.Job?) {
        futureStub.executeJob(job)
    }

    fun ping(payload: Int = ODSettings.pingPayloadSize, reply: Boolean = false, callback: ((Long) -> Unit)? = null): ListenableFuture<ODProto.Ping>? {
        var timer = System.currentTimeMillis()

        val call = futureStub.ping(ODProto.Ping.newBuilder().setData(ByteString.copyFrom(ByteArray(payload))).setReply(reply).build())
        call.addListener(
                { if (callback != null && !call.isCancelled) callback(System.currentTimeMillis() - timer) },
                {
                    J -> pingExecutor.submit(J)
                    timer = System.currentTimeMillis()
                }
        )

        return call
    }

    fun advertiseWorkerStatus(request: ODProto.WorkerStatus?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun updateWorkers() {
        BrokerService.updateWorkers()
    }
}