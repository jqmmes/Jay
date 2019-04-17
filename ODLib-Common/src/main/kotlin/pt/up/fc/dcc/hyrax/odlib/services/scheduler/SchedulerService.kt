package pt.up.fc.dcc.hyrax.odlib.services.scheduler

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.grpc.SchedulerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.*
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.*
import kotlin.concurrent.thread

object SchedulerService {

    enum class WorkerConnectivityStatus {
        ONLINE,
        OFFLINE
    }

    private var server :GRPCServerBase? = null
    private val workers: MutableMap<String, Worker?> = hashMapOf()
    private val brokerGRPC = BrokerGRPCClient("127.0.0.1")
    private var scheduler : Scheduler? = null
    private val notifyListeners = LinkedList<((Worker?, WorkerConnectivityStatus) -> Unit)>()
    private var running = false

    internal var weights: ODProto.Weights = ODProto.Weights.newBuilder().setComputeTime(0.5f).setQueueSize(0.1f)
            .setRunningJobs(0.1f).setBattery(0.2f).setBandwidth(0.1f).build()

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
            SmartScheduler(),
            EstimatedTimeScheduler()
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
        if (running) return
        server = SchedulerGRPCServer(useNettyServer).start()
        running = true
        brokerGRPC.announceServiceStatus(ODProto.ServiceStatus.newBuilder().setType(ODProto.ServiceStatus.Type.SCHEDULER).setRunning(true).build()) {
            ODLogger.logInfo("SchedulerService, START, RUNNING")
        }
        thread {
            brokerGRPC.updateWorkers{ODLogger.logInfo("SchedulerService, START, RUNNING")}
        }
    }

    internal fun schedule(request: ODProto.Job?): ODProto.Worker? {
        if (scheduler == null) scheduler = schedulers[0]
        return scheduler?.scheduleJob(pt.up.fc.dcc.hyrax.odlib.structures.Job(request))
    }

    internal fun notifyWorkerUpdate(worker: Worker?) : ODProto.StatusCode {
        workers[worker!!.id] = worker
        for (listener in notifyListeners) listener.invoke(worker, WorkerConnectivityStatus.ONLINE)
        return ODProto.StatusCode.Success
    }

    internal fun notifyWorkerFailure(worker: Worker?): ODProto.StatusCode {
        ODLogger.logInfo("SchedulerService, NOTIFY_WORKER_FAILURE, WORKER_ID=${worker?.id}")
        if (worker!!.id in workers.keys) {
            workers.remove(worker.id)
            for (listener in notifyListeners) listener.invoke(worker, WorkerConnectivityStatus.OFFLINE)
            return ODProto.StatusCode.Success
        }
        return ODProto.StatusCode.Error
    }

    internal fun registerNotifyListener(listener: ((Worker?, WorkerConnectivityStatus) -> Unit)) {
        notifyListeners.addLast(listener)
    }

    internal fun listenForWorkers(listen: Boolean, callback: ((ODProto.Status) -> Unit)? = null) {
        if (listen) brokerGRPC.listenMulticastWorkers(callback = callback)
        else brokerGRPC.listenMulticastWorkers(true, callback)
    }

    fun stop(stopGRPCServer: Boolean = true) {
        running = false
        if (stopGRPCServer) server?.stop()
        brokerGRPC.announceServiceStatus(ODProto.ServiceStatus.newBuilder().setType(ODProto.ServiceStatus.Type.SCHEDULER).setRunning(false).build()) {
            ODLogger.logInfo("SchedulerService, STOP")
        }
    }

    fun stopService(callback: ((ODProto.Status?) -> Unit)) {
        stop(false)
        callback(ODUtils.genStatusSuccess())
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    internal fun listSchedulers() : ODProto.Schedulers {
        val schedulersProto = ODProto.Schedulers.newBuilder()
        for (scheduler in schedulers) schedulersProto.addScheduler(scheduler.getProto())
        return schedulersProto.build()

    }

    internal fun setScheduler(id: String?) : ODProto.StatusCode {
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

    internal fun enableHeartBeat(workerTypes: ODProto.WorkerTypes, callback: (ODProto.Status) -> Unit) {
        brokerGRPC.enableHearBeats(workerTypes, callback)
    }

    internal fun enableBandwidthEstimates(bandwidthEstimateConfig: ODProto.BandwidthEstimate, callback: (ODProto.Status) -> Unit) {
        brokerGRPC.enableBandwidthEstimates(bandwidthEstimateConfig, callback)
    }

    internal fun disableHeartBeat() {
        brokerGRPC.disableHearBeats()
    }

    internal fun disableBandwidthEstimates() {
        brokerGRPC.disableBandwidthEstimates()
    }

    internal fun updateWeights(newWeights: ODProto.Weights?): ODProto.Status? {
        if (newWeights == null || (newWeights.computeTime + newWeights.queueSize + newWeights.runningJobs + newWeights
                        .bandwidth + newWeights.battery != 1.0f))
            return ODUtils.genStatus(ODProto.StatusCode.Error)
        weights = newWeights
        return ODUtils.genStatus(ODProto.StatusCode.Success)
    }

    internal fun isRunning() : Boolean { return running }

}