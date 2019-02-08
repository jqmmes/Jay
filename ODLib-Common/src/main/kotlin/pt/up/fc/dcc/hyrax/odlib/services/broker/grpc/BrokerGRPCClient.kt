package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import com.google.common.util.concurrent.ListenableFuture
import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerGrpc
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
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

    fun putJob(job: ODJob) {
        println("putJob BrokerGRPCClient")
        futureStub.putJob(ODUtils.genJobRequest(job))
        //blockingStub.putJob(ODUtils.genJobRequest("", data))
    }

    fun ping(data: ByteArray = ByteArray(0), callback: ((Long) -> Unit)? = null): ListenableFuture<ODProto.Ping>? {
        var timer = System.currentTimeMillis()

        val call = futureStub.ping(ODProto.Ping.newBuilder().setData(ByteString.copyFrom(data)).build())
        call.addListener(
                { if (callback != null && !call.isCancelled) callback(System.currentTimeMillis() - timer) },
                {
                    J -> pingExecutor.submit(J)
                    timer = System.currentTimeMillis()
                }
        )

        return call
    }
}