package pt.up.fc.dcc.hyrax.odlib.services.scheduler

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc.SchedulerGRPCServer

object SchedulerService {

    private var server :GRPCServerBase? = null

    fun start(useNettyServer: Boolean = false) {
        server = SchedulerGRPCServer(useNettyServer).start()
    }

    internal fun scheduleJob(id: Long): RemoteODClient {
        return RemoteODClient("127.0.0.1")
    }

    fun stop() {
        if (server != null) server!!.stop()
    }
}