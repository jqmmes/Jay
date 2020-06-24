package pt.up.fc.dcc.hyrax.jay.services.worker

import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.*
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.BatteryMonitor
import pt.up.fc.dcc.hyrax.jay.services.worker.grpc.WorkerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TaskExecutor
import pt.up.fc.dcc.hyrax.jay.structures.Detection
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.AbstractTaskExecutorManager as TaskExecutorManager


object WorkerService {

    private var taskExecutorManager: TaskExecutorManager? = null

    private val jobQueue = LinkedBlockingQueue<RunnableJobObjects>()
    private var running = false
    private var executorThreadPool: ExecutorService = Executors.newSingleThreadExecutor()
    private var waitingResultsMap: HashMap<Int, (List<Detection?>) -> Unit> = HashMap()
    private var server: GRPCServerBase? = null
    private val broker = BrokerGRPCClient("127.0.0.1")
    internal val profiler = ProfilerGRPCClient("127.0.0.1")

    init {
        executorThreadPool.shutdown()
        JayLogger.logInfo("COMPLETE")
    }

    internal fun queueJob(job: WorkerJob, callback: ((Any) -> Unit)?): StatusCode {
        JayLogger.logInfo("INIT", job.id)
        if (!running) throw Exception("WorkerService not running")
        jobQueue.put(RunnableJobObjects(job, callback))
        WorkerProfiler.atomicOperation(WorkerProfiler.totalJobs, increment = true)
        JayLogger.logInfo("JOB_QUEUED", job.id, "JOBS_IN_QUEUE=${WorkerProfiler.totalJobs.get() - WorkerProfiler.runningJobs.get()}")
        return if (callback == null) StatusCode.Success else StatusCode.Waiting
    }

    fun start(taskExecutorManager: TaskExecutorManager, useNettyServer: Boolean = false, batteryMonitor: BatteryMonitor? = null) {
        JayLogger.logInfo("INIT")
        if (running) return
        if (executorThreadPool.isShutdown || executorThreadPool.isTerminated) executorThreadPool = Executors.newFixedThreadPool(JaySettings.WORKING_THREADS)
        this.taskExecutorManager = taskExecutorManager
        WorkerProfiler.setBatteryMonitor(batteryMonitor)
        server = WorkerGRPCServer(useNettyServer).start()
        WorkerProfiler.start()
        thread(start = true, isDaemon = true, name = "WorkerService") {
            running = true
            while (running) {
                try {
                    if (!(executorThreadPool.isShutdown || executorThreadPool.isTerminated) && running) {
                        val job = jobQueue.take()
                        JayLogger.logInfo("DEQUEUE_TO_EXECUTOR", job.job?.id ?: "")
                        executorThreadPool.execute(job)
                    } else running = false

                } catch (e: Exception) {
                    running = false
                }
            }
            if (!executorThreadPool.isShutdown) executorThreadPool.shutdownNow()
        }
        broker.announceServiceStatus(
                ServiceStatus.newBuilder().setType(ServiceStatus.Type.WORKER).setRunning(true).build())
        { JayLogger.logInfo("RUNNING") }
    }

    fun stop(stopGRPCServer: Boolean = true, callback: ((Status?) -> Unit)? = null) {
        JayLogger.logInfo("INIT")
        running = false
        if (stopGRPCServer) server?.stop()
        waitingResultsMap.clear()
        jobQueue.clear()
        jobQueue.offer(RunnableJobObjects(null) {})
        executorThreadPool.shutdownNow()
        WorkerProfiler.destroy()
        taskExecutorManager?.getCurrentExecutor()?.destroy()
        broker.announceServiceStatus(ServiceStatus.newBuilder().setType(ServiceStatus.Type.WORKER).setRunning(false).build()) { S ->
            JayLogger.logInfo("COMPLETE")
            callback?.invoke(S)
        }
    }

    fun monitorBattery() {
        JayLogger.logInfo("INIT")
        WorkerProfiler.monitorBattery()
        JayLogger.logInfo("COMPLETE")
    }

    fun selectTaskExecutor(taskExecutorUUID: String, callback: ((Status?) -> Unit)?) {
        callback?.invoke(if (taskExecutorManager?.setExecutor(taskExecutorUUID) == true) JayUtils.genStatusSuccess() else JayUtils.genStatusError())
    }

    fun listTaskExecutors(): Set<TaskExecutor> {
        return taskExecutorManager?.taskExecutors ?: emptySet()
    }

    fun callExecutorAction(action: String, callback: ((Status?, Any?) -> Unit)?, vararg args: Any) {
        taskExecutorManager?.getCurrentExecutor()?.callAction(action, callback, *args)
    }

    fun runExecutorAction(action: String, callback: ((Status?) -> Unit)?, vararg args: Any) {
        taskExecutorManager?.getCurrentExecutor()?.runAction(action, callback, *args)
    }

    internal fun isRunning(): Boolean {
        return running
    }

    fun stopService(callback: ((Status?) -> Unit)) {
        JayLogger.logInfo("INIT")
        stop(false) { S ->
            callback(S)
            JayLogger.logInfo("COMPLETE")
        }
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    fun setExecutorSettings(settingMap: Map<String, Any>, callback: ((Status?) -> Unit)?) {
        callback?.invoke(taskExecutorManager?.getCurrentExecutor()?.setSettings(settingMap))
    }

    private class RunnableJobObjects(val job: WorkerJob?, var callback: ((Any) -> Unit)?) : Runnable {
        override fun run() {
            JayLogger.logInfo("INIT", job?.id ?: "")
            WorkerProfiler.atomicOperation(WorkerProfiler.runningJobs, increment = true)
            WorkerProfiler.profileExecution { taskExecutorManager?.getCurrentExecutor()?.executeJob(job, callback) }
            WorkerProfiler.atomicOperation(WorkerProfiler.runningJobs, WorkerProfiler.totalJobs, increment = false)
        }
    }
}
