package pt.up.fc.dcc.hyrax.jay.services.worker

import pt.up.fc.dcc.hyrax.jay.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.jay.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto.StatusCode
import pt.up.fc.dcc.hyrax.jay.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.worker.grpc.WorkerGRPCServer
import pt.up.fc.dcc.hyrax.jay.services.worker.interfaces.BatteryMonitor
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.AbstractTaskExecutor
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TensorflowTaskExecutor
import pt.up.fc.dcc.hyrax.jay.structures.Detection
import pt.up.fc.dcc.hyrax.jay.structures.Model
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


object WorkerService {

    private lateinit var localDetect: DetectObjects
    private var taskExecutor: AbstractTaskExecutor<List<Detection>>? = null

    private val jobQueue = LinkedBlockingQueue<RunnableJobObjects>()
    private var running = false
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var waitingResultsMap: HashMap<Int, (List<Detection?>) -> Unit> = HashMap()
    private var server: GRPCServerBase? = null
    private val brokerGRPC = BrokerGRPCClient("127.0.0.1")
    internal var fsAssistant: FileSystemAssistant? = null

    init {
        executor.shutdown()
        JayLogger.logInfo("COMPLETE")
    }

    internal fun queueJob(job: JayProto.WorkerJob, callback: ((List<Detection>) -> Unit)?): StatusCode {
        JayLogger.logInfo("INIT", job.id)
        if (!running) throw Exception("WorkerService not running")
        jobQueue.put(RunnableJobObjects(job, callback))
        WorkerProfiler.atomicOperation(WorkerProfiler.totalJobs, increment = true)
        JayLogger.logInfo("JOB_QUEUED", job.id, "JOBS_IN_QUEUE=${WorkerProfiler.totalJobs.get() - WorkerProfiler.runningJobs.get()}")
        return if (callback == null) StatusCode.Success else StatusCode.Waiting
    }

    fun start(taskExecutor: TensorflowTaskExecutor<List<Detection>>, localDetect: DetectObjects, useNettyServer: Boolean = false, batteryMonitor: BatteryMonitor? = null, fsAssistant: FileSystemAssistant? = null) {
        JayLogger.logInfo("INIT")
        if (running) return
        if (executor.isShutdown || executor.isTerminated) executor = Executors.newFixedThreadPool(JaySettings.WORKING_THREADS)
        this.localDetect = localDetect
        this.fsAssistant = fsAssistant
        this.taskExecutor = taskExecutor
        WorkerProfiler.setBatteryMonitor(batteryMonitor)
        server = WorkerGRPCServer(useNettyServer).start()
        WorkerProfiler.start()

        thread(start = true, isDaemon = true, name = "WorkerService") {
            running = true
            while (running) {
                try {
                    if (!(executor.isShutdown || executor.isTerminated) && running) {
                        val job = jobQueue.take()
                        JayLogger.logInfo("DEQUEUE_TO_EXECUTOR", job.job?.id ?: "")
                        executor.execute(job)
                    } else running = false

                } catch (e: Exception) {
                    running = false
                }
            }
            if (!executor.isShutdown) executor.shutdownNow()
        }
        brokerGRPC.announceServiceStatus(JayProto.ServiceStatus.newBuilder().setType(JayProto.ServiceStatus.Type.WORKER).setRunning(true).build()) {
            JayLogger.logInfo("RUNNING")
        }
    }

    fun stop(stopGRPCServer: Boolean = true, callback: ((JayProto.Status?) -> Unit)? = null) {
        JayLogger.logInfo("INIT")
        running = false
        if (stopGRPCServer) server?.stop()
        waitingResultsMap.clear()
        jobQueue.clear()
        jobQueue.offer(RunnableJobObjects(null) {})
        executor.shutdownNow()
        WorkerProfiler.destroy()
        taskExecutor?.destroy()
        brokerGRPC.announceServiceStatus(JayProto.ServiceStatus.newBuilder().setType(JayProto.ServiceStatus.Type.WORKER).setRunning(false).build()) { S ->
            JayLogger.logInfo("COMPLETE")
            callback?.invoke(S)
        }
    }

    fun monitorBattery() {
        JayLogger.logInfo("INIT")
        WorkerProfiler.monitorBattery()
        JayLogger.logInfo("COMPLETE")
    }

    fun loadModel(model: Model, callback: ((JayProto.Status) -> Unit)? = null) {
        JayLogger.logInfo("INIT")
        if (running) {
            taskExecutor?.runAction("loadModel", callback, model)
        }
        JayLogger.logInfo("COMPLETE")
    }


    fun listModels(): Set<Model> {
        JayLogger.logInfo("INIT")
        return taskExecutor?.callAction("listModels", {}) ?: emptySet()
    }

    internal fun isRunning(): Boolean {
        return running
    }

    fun stopService(callback: ((JayProto.Status?) -> Unit)) {
        JayLogger.logInfo("INIT")
        stop(false) { S ->
            callback(S)
            JayLogger.logInfo("COMPLETE")
        }
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    private class RunnableJobObjects(val job: JayProto.WorkerJob?, var callback: ((List<Detection>) -> Unit)?) : Runnable {
        override fun run() {
            JayLogger.logInfo("INIT", job?.id ?: "")
            WorkerProfiler.atomicOperation(WorkerProfiler.runningJobs, increment = true)
            WorkerProfiler.profileExecution { taskExecutor?.executeJob(job, callback) }
            WorkerProfiler.atomicOperation(WorkerProfiler.runningJobs, WorkerProfiler.totalJobs, increment = false)
        }
    }
}
