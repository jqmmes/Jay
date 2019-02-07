package pt.up.fc.dcc.hyrax.odlib.grpc

class AndroidClientGRPC {
}

/*package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

class BrokerGRPCClient(host: String) : GRPCClientBase<BrokerGrpc.BrokerBlockingStub, BrokerGrpc.BrokerFutureStub>
(host, ODSettings.brokerPort) {
    override var blockingStub: BrokerGrpc.BrokerBlockingStub = BrokerGrpc.newBlockingStub(channel)
    override var futureStub: BrokerGrpc.BrokerFutureStub = BrokerGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = BrokerGrpc.newBlockingStub(channel)
        futureStub = BrokerGrpc.newFutureStub(channel)
    }
}*/