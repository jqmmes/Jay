package pt.up.fc.dcc.hyrax.odlib.services.broker

import pt.up.fc.dcc.hyrax.odlib.structures.Worker
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Job
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Model
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Models
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Results
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Scheduler
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Schedulers
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Status
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker.Type
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.services.broker.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.services.broker.multicast.MulticastListener
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc.SchedulerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.worker.grpc.WorkerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.lang.Thread.sleep
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker as ODWorker

object BrokerService {

    private var server: GRPCServerBase? = null
    private val workers: MutableMap<String, Worker> = hashMapOf()
    private val scheduler = SchedulerGRPCClient("127.0.0.1")
    private val worker = WorkerGRPCClient("127.0.0.1")
    private val local: Worker = Worker(address = "127.0.0.1", type = Type.LOCAL)
    private val cloud = Worker(address = ODSettings.cloudIp, type = Type.CLOUD)

    init {
        workers[local.id] = local
        workers[cloud.id] = cloud
    }

    fun start(useNettyServer: Boolean = false) {
        server = BrokerGRPCServer(useNettyServer).start()
    }

    fun stop() {
        if (server != null) server!!.stop()
    }

    internal fun executeJob(request: Job?, callback: ((Results?) -> Unit)? = null) {
        worker.execute(request, callback)
    }

    internal fun scheduleJob(request: Job?, callback: ((Results?) -> Unit)? = null) {
        scheduler.schedule(request) { W ->
            if (W == null) callback?.invoke(null) else workers[W.id]!!.grpc.executeJob(request, callback)
        }
    }

    internal fun updateWorkers() {
        for (worker in workers.values) scheduler.notify(worker.getProto()) { S -> println("updateWorkers: ${S?.code?.name}") }
        sleep(1000)
    }

    internal fun getModels(callback: (Models) -> Unit) {
        worker.listModels(callback)
    }

    internal fun setModel(request: Model?, callback: ((Status?) -> Unit)? = null) {
        worker.selectModel(request, callback)
    }

    internal fun diffuseWorkerStatus() {
        // TODO: Validar que o Scheduler está a funcionar
        for (client in workers.values) if (client.type == Type.REMOTE) client.grpc.advertiseWorkerStatus(local.getProto())
    }

    internal fun updateWorker(request: ODWorker?, updateCloud: Boolean = false) {
        if (updateCloud){
            cloud.updateStatus(request)
            scheduler.notify(cloud.getProto()) {S -> println("updateWorker: ${S?.code?.name}")}
        } else {
            local.updateStatus(request)
            scheduler.notify(local.getProto()) {S -> println("updateWorker: ${S?.code?.name}")}
        }

    }

    internal fun receiveWorkerStatus(request: ODWorker?) {
        workers[request?.id]?.updateStatus(request)
        scheduler.notify(request) {S -> println("receiveWorkerStatus: ${S?.code?.name}")}
    }

    fun getSchedulers(callback: ((Schedulers?) -> Unit)? = null) {
        scheduler.listSchedulers(callback)

    }

    fun setScheduler(request: Scheduler?, callback: ((Status?) -> Unit)? = null) {
        scheduler.setScheduler(request, callback)
    }

    internal fun listenMulticast(stopListener: Boolean = false) {
        if (stopListener) MulticastListener.stop()
        else MulticastListener.listen(callback = { W, A -> checkWorker(W, A) })
    }

    private fun checkWorker(worker: ODProto.Worker?, address: String) {
        if (worker == null) return
        if (worker.id !in workers) workers[worker.id] = Worker(worker, address)
        else workers[worker.id]?.updateStatus(worker)
    }

    internal fun announceMulticast(stopAdvertiser: Boolean = false, worker: ODWorker? = null) {
        if (stopAdvertiser) MulticastAdvertiser.stop()
        else {
            val data = worker?.toByteArray() ?: ByteArray(0)
            if (MulticastAdvertiser.isRunning()) MulticastAdvertiser.setAdvertiseData(data)
            else MulticastAdvertiser.start(data)
        }
    }
}
