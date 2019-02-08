package pt.up.fc.dcc.hyrax.odlib.services.broker

import pt.up.fc.dcc.hyrax.odlib.clients.Client
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCServer

object BrokerService {

    private var server: GRPCServerBase? = null
    private val clients: MutableMap<String, Client> = hashMapOf()
    private var localClient: String

    init {
        val localClient = Client(address = "127.0.0.1")
        clients[localClient.id] = localClient
        this.localClient = localClient.id
    }

    fun start(useNettyServer: Boolean = false) {
        server = BrokerGRPCServer(useNettyServer).start()
    }

    fun stop() {
        if (server != null) server!!.stop()
    }
}