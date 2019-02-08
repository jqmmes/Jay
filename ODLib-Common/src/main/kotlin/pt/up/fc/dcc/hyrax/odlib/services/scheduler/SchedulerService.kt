package pt.up.fc.dcc.hyrax.odlib.services.scheduler

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc.SchedulerGRPCServer

object SchedulerService {

    private var server :GRPCServerBase? = null
    private val clients: MutableMap<String, ODProto.WorkerStatus?> = hashMapOf()
    private val brokerGRPC = BrokerGRPCClient("127.0.0.1")

    fun start(useNettyServer: Boolean = false) {
        server = SchedulerGRPCServer(useNettyServer).start()
        brokerGRPC.updateWorkers()
    }

    internal fun schedule(request: ODProto.Job?): String {
        return clients.keys.first()
    }

    internal fun notify(status: ODProto.WorkerStatus?) {
        clients[status!!.id] = status
    }

    fun stop() {
        if (server != null) server!!.stop()
    }
}