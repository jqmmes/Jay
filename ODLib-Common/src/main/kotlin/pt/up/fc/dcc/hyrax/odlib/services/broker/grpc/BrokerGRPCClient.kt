package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BrokerGRPCClient(host: String) : GRPCClientBase<BrokerGrpc.BrokerBlockingStub, BrokerGrpc.BrokerFutureStub>
(host, ODSettings.brokerPort) {
    override val threadPool: ExecutorService = Executors.newSingleThreadExecutor()!!
    override var blockingStub: BrokerGrpc.BrokerBlockingStub = BrokerGrpc.newBlockingStub(channel)
    override var futureStub: BrokerGrpc.BrokerFutureStub = BrokerGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = BrokerGrpc.newBlockingStub(channel)
        futureStub = BrokerGrpc.newFutureStub(channel)
    }

    fun putJob(data: ByteArray) {
        println("putJob BrokerGRPCClient")
        blockingStub.putJob(ODUtils.genJobRequest("", data))
    }
}