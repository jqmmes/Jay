package pt.up.fc.dcc.hyrax.odlib.services.broker

import pt.up.fc.dcc.hyrax.odlib.clients.Worker
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc.SchedulerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.worker.grpc.WorkerGRPCClient
import javax.security.sasl.AuthorizeCallback

object BrokerService {

    private var server: GRPCServerBase? = null
    private val workers: MutableMap<String, Worker> = hashMapOf()
    private val scheduler = SchedulerGRPCClient("127.0.0.1")
    private val worker = WorkerGRPCClient("127.0.0.1")


    init {
        val local = Worker(address = "127.0.0.1")
        workers[local.id] = local
    }

    fun start(useNettyServer: Boolean = false) {
        server = BrokerGRPCServer(useNettyServer).start()
    }

    fun stop() {
        if (server != null) server!!.stop()
    }

    internal fun executeJob(request: ODProto.Job?) {
        worker.execute(request)
    }

    internal fun scheduleJob(request: ODProto.Job?, callback: ((ODProto.JobResults?) -> Unit)? = null) {
        //scheduler.schedule(request!!) { worker -> println(worker.id); workers[worker.id]!!.grpc.executeJob(request, callback) }
        for (worker in workers.keys) scheduler.notify(ODProto.WorkerStatus.newBuilder().setId(worker).build())
    }

    internal fun diffuseWorkerStatus(request: ODProto.WorkerStatus?) {
        for (client in workers.values) client.grpc.advertiseWorkerStatus(request)
    }

    internal fun advertiseWorkerStatus(request: ODProto.WorkerStatus?) { scheduler.notify(request) }

    internal fun updateWorkers() {
        for (worker in workers.keys) scheduler.notify(ODProto.WorkerStatus.newBuilder().setId(worker).build())
    }
}