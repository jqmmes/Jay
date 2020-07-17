package pt.up.fc.dcc.hyrax.jay.services.scheduler

import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.ServiceStatus.Type.SCHEDULER
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Worker
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.BatteryMonitor
import pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc.SchedulerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers.AbstractScheduler
import pt.up.fc.dcc.hyrax.jay.structures.Task
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
    internal val broker = BrokerGRPCClient("127.0.0.1")
    internal val profiler = ProfilerGRPCClient("127.0.0.1")
    private var scheduler: AbstractScheduler? = null
    private val notifyListeners = LinkedList<((Worker?, WorkerConnectivityStatus) -> Unit)>()
    private var taskCompleteListener: ((String) -> Unit)? = null
    private var running = false
    private val schedulers: MutableSet<AbstractScheduler> = mutableSetOf()

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

    fun registerScheduler(scheduler: AbstractScheduler) {
        schedulers.add(scheduler)
    }

    fun start(useNettyServer: Boolean = false, batteryMonitor: BatteryMonitor? = null) {
        JayLogger.logInfo("INIT")
        if (running) return
        this.batteryMonitor = batteryMonitor
        this.server = SchedulerGRPCServer(useNettyServer).start()
        this.running = true
        this.broker.announceServiceStatus(JayProto.ServiceStatus.newBuilder().setType(SCHEDULER).setRunning(true).build()) {
            JayLogger.logInfo("COMPLETE")
        }
        thread {
            this.broker.notifySchedulerForAvailableWorkers { JayLogger.logInfo("COMPLETE") }
        }
    }

    internal fun schedule(request: JayProto.TaskDetails?): Worker? {
        //if (scheduler == null) scheduler = schedulers[0]
        if (scheduler == null) scheduler = schedulers.first()
        return scheduler?.scheduleTask(Task(request))
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

    internal fun registerNotifyTaskListener(listener: ((String) -> Unit)) {
        this.taskCompleteListener = listener
    }

    internal fun listenForWorkers(listen: Boolean, callback: ((JayProto.Status) -> Unit)? = null) {
        if (listen) broker.listenMulticastWorkers(callback = callback)
        else broker.listenMulticastWorkers(true, callback)
    }

    fun stop(stopGRPCServer: Boolean = true) {
        running = false
        if (stopGRPCServer) server?.stop()
        broker.announceServiceStatus(JayProto.ServiceStatus.newBuilder().setType(SCHEDULER).setRunning(false).build()) {
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

    internal fun isRunning(): Boolean {
        return running
    }

    internal fun notifyTaskComplete(id: String?) {
        taskCompleteListener?.invoke(id ?: "")
    }

    internal fun getExpectedCurrent(worker: Worker?, callback: ((JayProto.CurrentEstimations?) -> Unit)) {
        if (worker?.type == Worker.Type.LOCAL) callback(profiler.getExpectedCurrent())
        broker.getExpectedCurrentFromRemote(worker) { callback(it) }
    }

    fun setSchedulerSettings(settingMap: Map<String, Any>, callback: ((JayProto.Status?) -> Unit)?) {
        callback?.invoke(this.scheduler?.setSettings(settingMap))
    }

}