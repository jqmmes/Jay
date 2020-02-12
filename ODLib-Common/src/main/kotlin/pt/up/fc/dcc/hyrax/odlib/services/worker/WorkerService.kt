package pt.up.fc.dcc.hyrax.odlib.services.worker

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.StatusCode
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.worker.grpc.WorkerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.services.worker.workers.AbstractWorker
import pt.up.fc.dcc.hyrax.odlib.services.worker.workers.TensorflowWorker
import pt.up.fc.dcc.hyrax.odlib.structures.Detection
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


object WorkerService {

    private lateinit var localDetect: DetectObjects
    private var taskExecutor: AbstractWorker<List<Detection>>? = null

    private val jobQueue = LinkedBlockingQueue<RunnableJobObjects>()
    private var running = false
    private var executor : ExecutorService = Executors.newSingleThreadExecutor()
    private var waitingResultsMap : HashMap<Int, (List<Detection?>) -> Unit> = HashMap()
    private var server: GRPCServerBase? = null
    private val brokerGRPC = BrokerGRPCClient("127.0.0.1")
    internal var fsAssistant: FileSystemAssistant? = null

    init {
        executor.shutdown()
        ODLogger.logInfo("COMPLETE")
    }

    internal fun queueJob(job: ODProto.WorkerJob, callback: ((List<Detection>) -> Unit)?): StatusCode {
        ODLogger.logInfo("INIT", job.id)
        if (!running) throw Exception("WorkerService not running")
        jobQueue.put(RunnableJobObjects(job, callback))
        WorkerProfiler.atomicOperation(WorkerProfiler.totalJobs, increment = true)
        ODLogger.logInfo("JOB_QUEUED", job.id,"JOBS_IN_QUEUE=${WorkerProfiler.totalJobs.get() - WorkerProfiler.runningJobs.get()}")
        return if (callback == null) StatusCode.Success else StatusCode.Waiting
    }

    fun start(taskExecutor: TensorflowWorker<List<Detection>>, localDetect: DetectObjects, useNettyServer: Boolean = false, batteryMonitor: BatteryMonitor? = null, fsAssistant: FileSystemAssistant? = null) {
        ODLogger.logInfo("INIT")
        if (running) return
        if (executor.isShutdown || executor.isTerminated) executor = Executors.newFixedThreadPool(ODSettings.WORKING_THREADS)
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
                        ODLogger.logInfo("DEQUEUE_TO_EXECUTOR", job.job?.id ?: "")
                        executor.execute(job)
                    } else running = false

                } catch (e: Exception) { running = false }
            }
            if (!executor.isShutdown) executor.shutdownNow()
        }
        brokerGRPC.announceServiceStatus(ODProto.ServiceStatus.newBuilder().setType(ODProto.ServiceStatus.Type.WORKER).setRunning(true).build()) {
            ODLogger.logInfo("RUNNING")
        }
    }

    fun stop(stopGRPCServer: Boolean = true, callback: ((ODProto.Status?) -> Unit)? = null) {
        ODLogger.logInfo("INIT")
        running = false
        if (stopGRPCServer) server?.stop()
        waitingResultsMap.clear()
        jobQueue.clear()
        jobQueue.offer(RunnableJobObjects(null) {})
        executor.shutdownNow()
        WorkerProfiler.destroy()
        taskExecutor?.destroy()
        brokerGRPC.announceServiceStatus(ODProto.ServiceStatus.newBuilder().setType(ODProto.ServiceStatus.Type.WORKER).setRunning(false).build()) {S ->
            ODLogger.logInfo("COMPLETE")
            callback?.invoke(S)
        }
    }

    fun monitorBattery() {
        ODLogger.logInfo("INIT")
        WorkerProfiler.monitorBattery()
        ODLogger.logInfo("COMPLETE")
    }

    fun loadModel(model: Model, callback: ((ODProto.Status) -> Unit)? = null) {
        ODLogger.logInfo("INIT")
        if (running) {
            taskExecutor?.runAction("loadModel", callback, model)
        }
        ODLogger.logInfo("COMPLETE")
    }


    fun listModels() : Set<Model> {
        ODLogger.logInfo("INIT")
        return taskExecutor?.callAction("listModels", {}) ?: emptySet()
    }

    internal fun isRunning() : Boolean { return running }

    fun stopService(callback: ((ODProto.Status?) -> Unit)) {
        ODLogger.logInfo("INIT")
        stop(false) {S ->
            callback(S)
            ODLogger.logInfo("COMPLETE")}
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    private class RunnableJobObjects(val job: ODProto.WorkerJob?, var callback: ((List<Detection>) -> Unit)?) : Runnable {
        override fun run() {
            ODLogger.logInfo("INIT", job?.id ?: "")
            WorkerProfiler.atomicOperation(WorkerProfiler.runningJobs, increment = true)
            WorkerProfiler.profileExecution { taskExecutor?.executeJob(job, callback) }
            WorkerProfiler.atomicOperation(WorkerProfiler.runningJobs, WorkerProfiler.totalJobs, increment = false)
        }
    }
}
