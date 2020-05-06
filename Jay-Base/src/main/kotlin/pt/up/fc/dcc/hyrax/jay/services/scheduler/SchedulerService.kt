package pt.up.fc.dcc.hyrax.jay.services.scheduler

import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Worker
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc.SchedulerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers.*
import pt.up.fc.dcc.hyrax.jay.services.worker.status.device.battery.BatteryMonitor
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.*
import kotlin.concurrent.thread

object SchedulerService {

    enum class WorkerConnectivityStatus {
        ONLINE,
        OFFLINE
    }

    private var batteryMonitor: BatteryMonitor? = null
    private var server: GRPCServerBase? = null
    private val workers: MutableMap<String, Worker?> = hashMapOf()
    private val brokerGRPC = BrokerGRPCClient("127.0.0.1")
    private var scheduler: AbstractScheduler? = null
    private val notifyListeners = LinkedList<((Worker?, WorkerConnectivityStatus) -> Unit)>()
    private var jobCompleteListener: ((String) -> Unit)? = null
    private var running = false

    internal var weights: JayProto.Weights = JayProto.Weights.newBuilder().setComputeTime(0.5f).setQueueSize(0.1f)
            .setRunningJobs(0.1f).setBattery(0.2f).setBandwidth(0.1f).build()

    private val schedulers: Array<AbstractScheduler> = arrayOf(
            SingleDeviceScheduler(Worker.Type.LOCAL),
            SingleDeviceScheduler(Worker.Type.CLOUD),
            SingleDeviceScheduler(Worker.Type.REMOTE),
            MultiDeviceScheduler(true, Worker.Type.LOCAL),
            MultiDeviceScheduler(true, Worker.Type.REMOTE),
            MultiDeviceScheduler(true, Worker.Type.CLOUD),
            MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.CLOUD),
            MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.REMOTE),
            MultiDeviceScheduler(true, Worker.Type.CLOUD, Worker.Type.REMOTE),
            MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.CLOUD, Worker.Type.REMOTE),
            MultiDeviceScheduler(false, Worker.Type.LOCAL),
            MultiDeviceScheduler(false, Worker.Type.REMOTE),
            MultiDeviceScheduler(false, Worker.Type.CLOUD),
            MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.CLOUD),
            MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.REMOTE),
            MultiDeviceScheduler(false, Worker.Type.CLOUD, Worker.Type.REMOTE),
            MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.CLOUD, Worker.Type.REMOTE),
            SmartScheduler(),
            EstimatedTimeScheduler(),
            ComputationEstimateScheduler()
    )

    internal fun getWorker(id: String) : Worker? {
        return if (workers.containsKey(id)) workers[id] else null
    }

    internal fun getWorkers(vararg filter: Worker.Type): HashMap<String, Worker?> {
        return getWorkers(filter.asList())
    }

    internal fun getWorkers(filter: List<Worker.Type>): HashMap<String, Worker?> {
        if (filter.isEmpty()) return workers as HashMap<String, Worker?>
        val filteredWorkers = mutableMapOf<String, Worker?>()
        for (worker in workers) {
            if (worker.value?.type in filter) filteredWorkers[worker.key] = worker.value
        }
        return filteredWorkers as HashMap<String, Worker?>
    }

    fun start(useNettyServer: Boolean = false, batteryMonitor: BatteryMonitor? = null) {
        JayLogger.logInfo("INIT")
        if (running) return
        this.batteryMonitor = batteryMonitor
        this.server = SchedulerGRPCServer(useNettyServer).start()
        this.running = true
        this.brokerGRPC.announceServiceStatus(JayProto.ServiceStatus.newBuilder().setType(JayProto.ServiceStatus.Type.SCHEDULER).setRunning(true).build()) {
            JayLogger.logInfo("COMPLETE")
        }
        thread {
            this.brokerGRPC.updateWorkers { JayLogger.logInfo("COMPLETE") }
        }
    }

    internal fun schedule(request: JayProto.JobDetails?): Worker? {
        if (scheduler == null) scheduler = schedulers[0]
        return scheduler?.scheduleJob(pt.up.fc.dcc.hyrax.jay.structures.Job(request))
    }

    internal fun notifyWorkerUpdate(worker: Worker?): JayProto.StatusCode {
        JayLogger.logInfo("WORKER_UPDATE", actions = *arrayOf("WORKER_ID=${worker?.id}", "WORKER_TYPE=${worker?.type?.name}"))
        if (worker?.type == Worker.Type.REMOTE && JaySettings.CLOUDLET_ID != "" && JaySettings.CLOUDLET_ID != worker.id) return JayProto.StatusCode.Success
        workers[worker!!.id] = worker
        for (listener in notifyListeners) listener.invoke(worker, WorkerConnectivityStatus.ONLINE)
        return JayProto.StatusCode.Success
    }

    internal fun notifyWorkerFailure(worker: Worker?): JayProto.StatusCode {
        JayLogger.logInfo("WORKER_FAILED", actions = *arrayOf("WORKER_ID=${worker?.id}", "WORKER_TYPE=${worker?.type?.name}"))
        if (worker!!.id in workers.keys) {
            workers.remove(worker.id)
            for (listener in notifyListeners) listener.invoke(worker, WorkerConnectivityStatus.OFFLINE)
            return JayProto.StatusCode.Success
        }
        return JayProto.StatusCode.Error
    }

    internal fun registerNotifyListener(listener: ((Worker?, WorkerConnectivityStatus) -> Unit)) {
        notifyListeners.addLast(listener)
    }

    internal fun registerNotifyJobListener(listener: ((String) -> Unit)) {
        this.jobCompleteListener = listener
    }

    internal fun listenForWorkers(listen: Boolean, callback: ((JayProto.Status) -> Unit)? = null) {
        if (listen) brokerGRPC.listenMulticastWorkers(callback = callback)
        else brokerGRPC.listenMulticastWorkers(true, callback)
    }

    fun stop(stopGRPCServer: Boolean = true) {
        running = false
        if (stopGRPCServer) server?.stop()
        brokerGRPC.announceServiceStatus(JayProto.ServiceStatus.newBuilder().setType(JayProto.ServiceStatus.Type.SCHEDULER).setRunning(false).build()) {
            JayLogger.logInfo("STOP")
        }
    }

    fun stopService(callback: ((JayProto.Status?) -> Unit)) {
        stop(false)
        callback(JayUtils.genStatusSuccess())
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    internal fun listSchedulers(): JayProto.Schedulers {
        JayLogger.logInfo("INIT")
        val schedulersProto = JayProto.Schedulers.newBuilder()
        for (scheduler in schedulers) {
            JayLogger.logInfo("SCHEDULER_INFO", actions = *arrayOf("SCHEDULER_NAME=${scheduler.getName()}", "SCHEDULER_ID=${scheduler.id}")) //.replace(",", ";")
            schedulersProto.addScheduler(scheduler.getProto())
        }
        JayLogger.logInfo("COMPLETE")
        return schedulersProto.build()

    }

    internal fun setScheduler(id: String?): JayProto.StatusCode {
        for (scheduler in schedulers)
            if (scheduler.id == id) {
                this.scheduler?.destroy()
                this.scheduler = scheduler
                this.scheduler?.init()
                this.scheduler?.waitInit()
                return JayProto.StatusCode.Success
            }
        return JayProto.StatusCode.Error
    }

    internal fun enableHeartBeat(workerTypes: JayProto.WorkerTypes, callback: (JayProto.Status) -> Unit) {
        brokerGRPC.enableHearBeats(workerTypes, callback)
    }

    internal fun enableBandwidthEstimates(bandwidthEstimateConfig: JayProto.BandwidthEstimate, callback: (JayProto.Status) -> Unit) {
        brokerGRPC.enableBandwidthEstimates(bandwidthEstimateConfig, callback)
    }

    internal fun disableHeartBeat() {
        brokerGRPC.disableHearBeats()
    }

    internal fun disableBandwidthEstimates() {
        brokerGRPC.disableBandwidthEstimates()
    }

    internal fun updateWeights(newWeights: JayProto.Weights?): JayProto.Status? {
        if (newWeights == null || (newWeights.computeTime + newWeights.queueSize + newWeights.runningJobs + newWeights
                        .bandwidth + newWeights.battery != 1.0f))
            return JayUtils.genStatus(JayProto.StatusCode.Error)
        weights = newWeights
        return JayUtils.genStatus(JayProto.StatusCode.Success)
    }

    internal fun isRunning() : Boolean { return running }

    internal fun notifyJobComplete(id: String?) {
        jobCompleteListener?.invoke(id ?: "")
    }

}