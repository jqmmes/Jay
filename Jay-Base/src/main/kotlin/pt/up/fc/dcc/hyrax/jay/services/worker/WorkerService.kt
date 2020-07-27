package pt.up.fc.dcc.hyrax.jay.services.worker

import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.*
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.grpc.ProfilerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayState
import pt.up.fc.dcc.hyrax.jay.services.worker.grpc.WorkerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TaskExecutor
import pt.up.fc.dcc.hyrax.jay.structures.Detection
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.AbstractTaskExecutorManager as TaskExecutorManager


object WorkerService {

    private var taskExecutorManager: TaskExecutorManager? = null

    private val TASKS_LOCK = Object()

    private val taskQueue = LinkedBlockingQueue<RunnableTaskObjects>()
    private var running = false
    private var executorThreadPool: ExecutorService = Executors.newSingleThreadExecutor()
    private var waitingResultsMap: HashMap<Int, (List<Detection?>) -> Unit> = HashMap()
    private var server: GRPCServerBase? = null
    private val broker = BrokerGRPCClient("127.0.0.1")
    private val profiler = ProfilerGRPCClient("127.0.0.1")
    private val averageComputationTimes = CircularFifoQueue<Long>(JaySettings.AVERAGE_COMPUTATION_TIME_TO_SCORE)
    private var runningTasks: AtomicInteger = AtomicInteger(0)
    private var totalTasks: AtomicInteger = AtomicInteger(0)
    private var queueSize: Int = Int.MAX_VALUE

    init {
        executorThreadPool.shutdown()
        JayLogger.logInfo("COMPLETE")
    }

    internal fun queueTask(task: WorkerTask, callback: ((Any) -> Unit)?): StatusCode {
        JayLogger.logInfo("INIT", task.id)
        if (!running) throw Exception("WorkerService not running")
        taskQueue.put(RunnableTaskObjects(task) { R ->
            callback?.invoke(R)
        })
        atomicOperation(totalTasks, increment = true)
        JayLogger.logInfo("TASK_QUEUED", task.id, "TASKS_IN_QUEUE=${totalTasks.get() - runningTasks.get()}")
        return if (callback == null) StatusCode.Success else StatusCode.Waiting
    }

    fun start(taskExecutorManager: TaskExecutorManager, useNettyServer: Boolean = false) {
        JayLogger.logInfo("INIT")
        if (running) return
        if (executorThreadPool.isShutdown || executorThreadPool.isTerminated) executorThreadPool = Executors.newFixedThreadPool(JaySettings.WORKING_THREADS)
        this.taskExecutorManager = taskExecutorManager
        repeat(30) {
            if (this.server == null) {
                this.server = WorkerGRPCServer(useNettyServer).start()
                if (this.server == null) JaySettings.WORKER_PORT = Random.nextInt(30000, 64000)
            }
        }
        thread(start = true, isDaemon = true, name = "WorkerService") {
            running = true
            while (running) {
                try {
                    if (!(executorThreadPool.isShutdown || executorThreadPool.isTerminated) && running) {
                        val task = taskQueue.take()
                        JayLogger.logInfo("DEQUEUE_TO_EXECUTOR", task.task?.id ?: "")
                        executorThreadPool.execute(task)
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
        broker.enableWorkerStatusAdvertisement {
            if (it.code == StatusCode.Success) JayLogger.logInfo("WORKER_STATUS_ADVERTISEMENT_ENABLED")
            else JayLogger.logError("WORKER_STATUS_ADVERTISEMENT_FAILED")
        }
        profiler.startRecording()
    }

    fun stop(stopGRPCServer: Boolean = true, callback: ((Status?) -> Unit)? = null) {
        JayLogger.logInfo("INIT")
        running = false
        if (stopGRPCServer) server?.stop()
        waitingResultsMap.clear()
        taskQueue.clear()
        taskQueue.offer(RunnableTaskObjects(null) {})
        executorThreadPool.shutdownNow()
        taskExecutorManager?.getCurrentExecutor()?.destroy()
        runningTasks.set(0)
        totalTasks.set(0)
        broker.announceServiceStatus(ServiceStatus.newBuilder().setType(ServiceStatus.Type.WORKER).setRunning(false).build()) { S ->
            JayLogger.logInfo("COMPLETE")
            callback?.invoke(S)
        }
        profiler.stopRecording()
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

    private fun profileExecution(code: (() -> Unit)) {
        JayLogger.logInfo("START")
        val computationStartTimestamp = System.currentTimeMillis()
        profiler.setState(JayState.COMPUTE)
        code.invoke()
        profiler.unSetState(JayState.COMPUTE)
        val totalTime = System.currentTimeMillis() - computationStartTimestamp
        averageComputationTimes.add(totalTime)
        JayLogger.logInfo("END", actions = *arrayOf("COMPUTATION_TIME=$totalTime",
                "NEW_AVERAGE_COMPUTATION_TIME=${(this.averageComputationTimes.sum() / this.averageComputationTimes.size)}"))
    }

    private fun atomicOperation(vararg values: AtomicInteger, increment: Boolean = false) {
        synchronized(TASKS_LOCK) {
            for (value in values)
                if (increment) value.incrementAndGet() else value.decrementAndGet()
        }
    }

    fun getWorkerStatus(): WorkerComputeStatus? {
        val workerComputeStatusBuilder = WorkerComputeStatus.newBuilder()
        workerComputeStatusBuilder.avgTimePerTask = if (averageComputationTimes.size > 0) averageComputationTimes.sum() / averageComputationTimes.size else 0
        workerComputeStatusBuilder.queueSize = queueSize
        workerComputeStatusBuilder.runningTasks = runningTasks.get()
        workerComputeStatusBuilder.queuedTasks = totalTasks.get()
        return workerComputeStatusBuilder.build()
    }

    private class RunnableTaskObjects(val task: WorkerTask?, var callback: ((Any) -> Unit)?) : Runnable {
        override fun run() {
            JayLogger.logInfo("INIT", task?.id ?: "")
            atomicOperation(runningTasks, increment = true)
            profileExecution { taskExecutorManager?.getCurrentExecutor()?.executeTask(task, callback) }
            atomicOperation(runningTasks, totalTasks, increment = false)
        }
    }
}
