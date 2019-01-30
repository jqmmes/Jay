package pt.up.fc.dcc.hyrax.odlib.services.broker

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCServer

object BrokerService {

    private var server: GRPCServerBase? = null

    fun start() {
        server = BrokerGRPCServer().start()
    }

    fun stop() {
        if (server != null) server!!.stop()
    }
}