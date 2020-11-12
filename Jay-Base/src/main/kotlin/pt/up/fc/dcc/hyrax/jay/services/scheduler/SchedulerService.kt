package pt.up.fc.dcc.hyrax.jay.services.scheduler

import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.ServiceStatus.Type.SCHEDULER
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Worker
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power.PowerMonitor
import pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc.SchedulerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers.AbstractScheduler
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.random.Random

object SchedulerService {
    enum class WorkerConnectivityStatus {
        ONLINE,
        OFFLINE
    }

    private var powerMonitor: PowerMonitor? = null
    private var server: GRPCServerBase? = null
    private val workers: MutableMap<String, Worker?> = hashMapOf()
    internal val broker = BrokerGRPCClient("127.0.0.1")
    internal val profiler = ProfilerGRPCClient("127.0.0.1")
    private var scheduler: AbstractScheduler? = null
    private val notifyListeners = LinkedList<((Worker?, WorkerConnectivityStatus) -> Unit)>()
    private var taskCompleteListener: ((String) -> Unit)? = null
    private var running = false
    private val schedulers: MutableSet<AbstractScheduler> = mutableSetOf()
    private val workersLock = Object()
    private val concurrentFIFOQueueSemaphore = ConcurrentLinkedDeque<Semaphore>()
    private val concurrentSemaphoreLock = Object()

    internal fun getWorker(id: String): Worker? {
        return synchronized(workersLock) {
            if (workers.containsKey(id)) workers[id] else null
        }
    }

    internal fun getWorkers(vararg filter: Worker.Type): HashMap<String, Worker?> {
        return getWorkers(filter.asList())
    }

    internal fun getWorkers(filter: List<Worker.Type>): HashMap<String, Worker?> {
        if (filter.isEmpty()) return workers as HashMap<String, Worker?>
        val filteredWorkers = mutableMapOf<String, Worker?>()
        synchronized(workersLock) {
            for (worker in workers) {
                if (worker.value?.type in filter) filteredWorkers[worker.key] = worker.value
            }
        }
        return filteredWorkers as HashMap<String, Worker?>
    }

    fun registerScheduler(scheduler: AbstractScheduler) {
        schedulers.add(scheduler)
    }

    fun start(useNettyServer: Boolean = false, powerMonitor: PowerMonitor? = null) {
        JayLogger.logInfo("INIT")
        if (running) return
        this.powerMonitor = powerMonitor
        repeat(30) {
            if (this.server == null) {
                this.server = SchedulerGRPCServer(useNettyServer).start()
                if (this.server == null) JaySettings.SCHEDULER_PORT = Random.nextInt(30000, 64000)
            }
        }
        this.running = true
        this.broker.announceServiceStatus(JayProto.ServiceStatus.newBuilder().setType(SCHEDULER).setRunning(true).build()) {
            JayLogger.logInfo("COMPLETE")
        }
        thread {
            this.broker.notifySchedulerForAvailableWorkers { JayLogger.logInfo("COMPLETE") }
        }
    }

    internal fun schedule(request: JayProto.TaskDetails?): Worker? {
        if (scheduler == null) scheduler = schedulers.first()
        val w = scheduler?.scheduleTask(Task(request))
        JayLogger.logInfo("SELECTED_WORKER", request?.id ?: "", "WORKER=${w?.id}")
        if (w != null && workers.containsKey(w.id)) {
            broker.notifyAllocatedTask(JayProto.TaskAllocationNotification.newBuilder()
                    .setTaskId(request?.id ?: "")
                    .setWorkerId(w.id)
                    .build()
            ) {
                JayLogger.logInfo("WORKER_TASK_ALLOCATION_NOTIFIED", request?.id ?: "",
                        "STATUS=${it?.code?.name}", "WORKER=${w.id}"
                )
            }
            val semaphore = Semaphore(0)
            synchronized(concurrentSemaphoreLock) {
                concurrentFIFOQueueSemaphore.addLast(semaphore)
            }
            var queueIsFull: Boolean
            synchronized(workersLock) {
                queueIsFull = try {
                    if (workers[w.id]!!.queueSize > 1)
                        workers[w.id]!!.queuedTasks >= max(5, workers[w.id]!!.queueSize - 5)
                    else
                        false
                } catch (ignore: Exception) {
                    false
                }
            }
            while (queueIsFull) {
                try {
                    JayLogger.logInfo("WORKER_QUEUE_FULL", request?.id ?: "", "WORKER=${w.id}",
                            "CURRENT_QUEUE=${workers[w.id]?.queuedTasks}", "MAX_QUEUE=${workers[w.id]?.queueSize}")
                } catch (ignore: Exception) {
                }
                semaphore.acquire()
                synchronized(workersLock) {
                    queueIsFull = try {
                        workers[w.id]!!.queuedTasks >= max(5, workers[w.id]!!.queueSize - 5)
                    } catch (ignore: Exception) {
                        false
                    }
                }
            }
            synchronized(workersLock) {
                try {
                    workers[w.id] = Worker.newBuilder(workers[w.id]).setQueuedTasks(workers[w.id]!!.queuedTasks + 1).build()
                } catch (ignore: Exception) {
                }
            }
            synchronized(concurrentSemaphoreLock) {
                concurrentFIFOQueueSemaphore.remove(semaphore)
            }
            try {
                JayLogger.logInfo("INCREMENT_QUEUED_TASKS", request?.id ?: "", "WORKER=${w.id}",
                        "NEW_SIZE=${workers[w.id]?.queuedTasks}")
            } catch (ignore: Exception) {
            }
        }
        return w
    }

    internal fun notifyWorkerUpdate(worker: Worker?): JayProto.StatusCode {
        JayLogger.logInfo("WORKER_UPDATE", actions = arrayOf("WORKER_ID=${worker?.id}", "WORKER_TYPE=${worker?.type?.name}"))
        if (worker?.type == Worker.Type.REMOTE && JaySettings.CLOUDLET_ID != "" && JaySettings.CLOUDLET_ID != worker.id) return JayProto.StatusCode.Success
        synchronized(workersLock) {
            workers[worker!!.id] = worker
        }
        synchronized(concurrentSemaphoreLock) {
            concurrentFIFOQueueSemaphore.peekFirst()?.release()
        }
        JayLogger.logInfo(
                "WORKER=${worker!!.id}",
                "BAT_CAPACITY=${workers[worker.id]?.powerEstimations?.batteryCapacity}",
                "BAT_LEVEL=${workers[worker.id]?.powerEstimations?.batteryLevel}",
                "BAT_COMPUTE=${workers[worker.id]?.powerEstimations?.compute}",
                "BAT_IDLE=${workers[worker.id]?.powerEstimations?.idle}",
                "BAT_TX=${workers[worker.id]?.powerEstimations?.tx}",
                "BAT_RX=${workers[worker.id]?.powerEstimations?.rx}")
        for (listener in notifyListeners) listener.invoke(worker, WorkerConnectivityStatus.ONLINE)
        return JayProto.StatusCode.Success
    }

    internal fun notifyWorkerFailure(worker: Worker?): JayProto.StatusCode {
        JayLogger.logInfo("WORKER_FAILED", actions = arrayOf("WORKER_ID=${worker?.id}", "WORKER_TYPE=${worker?.type?.name}"))
        if (worker!!.id in workers.keys) {
            synchronized(workersLock) {
                workers.remove(worker.id)
            }
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
            JayLogger.logInfo("SCHEDULER_INFO", actions = arrayOf("SCHEDULER_NAME=${scheduler.getName()}", "SCHEDULER_ID=${scheduler.id}")) //.replace(",", ";")
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

    internal fun getExpectedPower(worker: Worker?, callback: ((JayProto.PowerEstimations?) -> Unit)) {
        if (worker?.type == Worker.Type.LOCAL) callback(profiler.getExpectedPower())
        broker.getExpectedPowerFromRemote(worker) { callback(it) }
    }

    internal fun canMeetDeadline(task: Task, worker: Worker?): Boolean {
        if (worker == null) return false
        JayLogger.logInfo("CAN_MEET_DEADLINE", task.id, "WORKER=${worker.id}")
        if ((task.deadlineDuration == null && task.deadline == null)
                || (task.deadlineDuration == 0L)
                || (task.creationTimeStamp == task.deadline)) {
            JayLogger.logInfo("NO_DEADLINE_SET", task.id, "WORKER=${worker.id}")
            return true
        }
        val deadlineTime: Long = task.deadline ?: System.currentTimeMillis() + ((task.deadlineDuration ?: 0) * 1000)
        val expectedCompletion = System.currentTimeMillis() + ((worker.queuedTasks + 1) * worker.avgTimePerTask + (worker.bandwidthEstimate * task.dataSize)).toLong()
        val deadlineTimeWithTolerance = deadlineTime - JaySettings.DEADLINE_CHECK_TOLERANCE
        JayLogger.logInfo("CHECK_WORKER_MEETS_DEADLINE", task.id,
                "WORKER=${worker.id}",
                "MAX_DEADLINE=${deadlineTime}",
                "EXPECTED_DEADLINE=${expectedCompletion}",
                "EXPECTED_DURATION=${((worker.queuedTasks + 1) * worker.avgTimePerTask + (worker.bandwidthEstimate * task.dataSize)).toLong()}",
                "QUEUE_SIZE=${worker.queuedTasks}",
                "AVG_TIME_PER_TASK=${worker.avgTimePerTask}",
                "TASK_DATA_SIZE=${task.dataSize}",
                "WORKER_BANDWIDTH=${worker.bandwidthEstimate}",
                "DEADLINE_WITH_TOLERANCE=${deadlineTimeWithTolerance}"
        )

        if (expectedCompletion <= deadlineTimeWithTolerance) {
            JayLogger.logInfo("WORKER_MEETS_DEADLINE", task.id, "WORKER=${worker.id};")
            return true
        }
        JayLogger.logInfo("WORKER_CANT_MEET_DEADLINE", task.id, "WORKER=${worker.id}")
        return false
    }

    fun setSchedulerSettings(settingMap: Map<String, Any>, callback: ((JayProto.Status?) -> Unit)?) {
        callback?.invoke(this.scheduler?.setSettings(settingMap))
    }
}