package pt.up.fc.dcc.hyrax.odlib.services.scheduler

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Job
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc.SchedulerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.MultiDeviceScheduler
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.Scheduler
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.SingleDeviceScheduler
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob

object SchedulerService {

    private var server :GRPCServerBase? = null
    private val workers: MutableMap<String, Worker?> = hashMapOf()
    private val brokerGRPC = BrokerGRPCClient("127.0.0.1")
    private var scheduler : Scheduler? = null

    private val schedulers: Array<Scheduler> = arrayOf(
            SingleDeviceScheduler(Worker.Type.LOCAL),
            SingleDeviceScheduler(Worker.Type.CLOUD),
            SingleDeviceScheduler(Worker.Type.REMOTE),
            MultiDeviceScheduler(true, Worker.Type.LOCAL),
            MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.CLOUD),
            MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.REMOTE),
            MultiDeviceScheduler(true, Worker.Type.CLOUD, Worker.Type.REMOTE),
            MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.CLOUD, Worker.Type.REMOTE),
            MultiDeviceScheduler(false, Worker.Type.LOCAL),
            MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.CLOUD),
            MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.REMOTE),
            MultiDeviceScheduler(false, Worker.Type.CLOUD, Worker.Type.REMOTE),
            MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.CLOUD, Worker.Type.REMOTE)
    )


    internal fun getWorkers(vararg filter: ODProto.Worker.Type): HashMap<String, Worker?> {
        return getWorkers(filter.asList())
    }

    internal fun getWorkers(filter: List<ODProto.Worker.Type>): HashMap<String, Worker?> {
        if (filter.isEmpty()) return workers as HashMap<String, Worker?>
        val filteredWorkers = mutableMapOf<String, Worker?>()
        for (worker in workers)
            if (worker.value?.type in filter) filteredWorkers[worker.key] = worker.value
        return filteredWorkers as HashMap<String, Worker?>
    }

    fun start(useNettyServer: Boolean = false) {
        server = SchedulerGRPCServer(useNettyServer).start()
        brokerGRPC.updateWorkers()
    }

    internal fun schedule(request: Job?): ODProto.Worker? {
        if (scheduler == null) scheduler = schedulers[0]
        scheduler?.scheduleJob(ODJob(request))
        var worker = scheduler?.scheduleJob(ODJob(request))
        println(worker)
        return worker
    }

    internal fun notify(worker: Worker?) {
        workers[worker!!.id] = worker
    }

    internal fun listenForWorkers(listen: Boolean) {
        if (listen) brokerGRPC.listenMulticastWorkers()
        else brokerGRPC.listenMulticastWorkers(true)
    }

    fun stop() {
        if (server != null) server!!.stop()
    }

    fun listSchedulers() : ODProto.Schedulers {
        val schedulersProto = ODProto.Schedulers.newBuilder()
        for (scheduler in schedulers) schedulersProto.addScheduler(scheduler.getProto())
        return schedulersProto.build()

    }

    fun setScheduler(id: String?) : ODProto.Status.Code {
        for (scheduler in schedulers)
            if (scheduler.id == id) {
                this.scheduler = scheduler
                return ODProto.Status.Code.Success
            }
        return ODProto.Status.Code.Error
    }
}