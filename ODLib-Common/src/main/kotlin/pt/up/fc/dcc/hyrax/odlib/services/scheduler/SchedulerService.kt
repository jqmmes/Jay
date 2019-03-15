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
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.SmartScheduler
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import java.util.*

object SchedulerService {

    private var server :GRPCServerBase? = null
    private val workers: MutableMap<String, Worker?> = hashMapOf()
    private val brokerGRPC = BrokerGRPCClient("127.0.0.1")
    private var scheduler : Scheduler? = null
    private val notifyListeners = LinkedList<((Worker?) -> Unit)>()

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
            MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.CLOUD, Worker.Type.REMOTE),
            SmartScheduler()
    )

    internal fun getWorker(id: String) : Worker? {
        return if (workers.containsKey(id)) workers[id] else null
    }

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
        return scheduler?.scheduleJob(ODJob(request))
    }

    internal fun notify(worker: Worker?) {
        workers[worker!!.id] = worker
        for (listener in notifyListeners) listener.invoke(worker)
    }

    internal fun registerNotifyListener(listener: ((Worker?) -> Unit)) {
        notifyListeners.addLast(listener)
    }

    internal fun listenForWorkers(listen: Boolean, callback: ((ODProto.Status) -> Unit)? = null) {
        println("listenForWorkers")
        if (listen) brokerGRPC.listenMulticastWorkers(callback = callback)
        else brokerGRPC.listenMulticastWorkers(true, callback)
    }

    fun stop() {
        if (server != null) server!!.stop()
    }

    fun listSchedulers() : ODProto.Schedulers {
        val schedulersProto = ODProto.Schedulers.newBuilder()
        for (scheduler in schedulers) schedulersProto.addScheduler(scheduler.getProto())
        return schedulersProto.build()

    }

    fun setScheduler(id: String?) : ODProto.StatusCode {
        for (scheduler in schedulers)
            if (scheduler.id == id) {
                this.scheduler?.destroy()
                this.scheduler = scheduler
                this.scheduler?.init()
                this.scheduler?.waitInit()
                return ODProto.StatusCode.Success
            }
        return ODProto.StatusCode.Error
    }
}