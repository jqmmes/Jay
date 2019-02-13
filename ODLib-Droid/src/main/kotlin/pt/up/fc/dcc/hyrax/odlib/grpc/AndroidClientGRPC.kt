package pt.up.fc.dcc.hyrax.odlib.grpc

class AndroidClientGRPC {
}

/*package pt.up.fc.dcc.hyrax.odlib.services.broker.grpc

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClientBase
import pt.up.fc.dcc.hyrax.odlib.protoc.BrokerServiceGrpc
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings

class BrokerGRPCClient(host: String) : GRPCClientBase<BrokerServiceGrpc.BrokerBlockingStub, BrokerServiceGrpc.BrokerFutureStub>
(host, ODSettings.brokerPort) {
    override var blockingStub: BrokerServiceGrpc.BrokerBlockingStub = BrokerServiceGrpc.newBlockingStub(channel)
    override var futureStub: BrokerServiceGrpc.BrokerFutureStub = BrokerServiceGrpc.newFutureStub(channel)

    override fun reconnectStubs() {
        blockingStub = BrokerServiceGrpc.newBlockingStub(channel)
        futureStub = BrokerServiceGrpc.newFutureStub(channel)
    }
}*/