package pt.up.fc.dcc.hyrax.odlib.services.scheduler

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc.SchedulerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.SchedulerBase
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob

object SchedulerService {

    private var server :GRPCServerBase? = null
    private val workers: MutableMap<String, ODProto.Worker?> = hashMapOf()
    private val brokerGRPC = BrokerGRPCClient("127.0.0.1")
    private var scheduler : SchedulerBase? = null

    internal fun getWorkers(): HashMap<String, ODProto.Worker?> {
        return workers as HashMap<String, ODProto.Worker?>
    }

    fun start(useNettyServer: Boolean = false) {
        server = SchedulerGRPCServer(useNettyServer).start()
        brokerGRPC.updateWorkers()
    }

    internal fun schedule(request: ODProto.Job?): String {
        scheduler?.scheduleJob(ODJob(request))
        return workers.keys.first()
    }

    internal fun notify(status: ODProto.Worker?) {
        workers[status!!.id] = status
    }

    fun stop() {
        if (server != null) server!!.stop()
    }
}