package pt.up.fc.dcc.hyrax.odlib.services.scheduler

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc.SchedulerGRPCServer

object SchedulerService {

    fun startService(useNettyServer: Boolean = false) {
        SchedulerGRPCServer(useNettyServer)
    }

    internal fun scheduleJob(id: Long): RemoteODClient {
        return ClientManager.getLocalODClient()
    }
}