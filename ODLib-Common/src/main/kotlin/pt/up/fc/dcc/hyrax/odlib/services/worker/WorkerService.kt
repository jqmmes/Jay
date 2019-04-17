package pt.up.fc.dcc.hyrax.odlib.services.worker

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.StatusCode
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.worker.grpc.WorkerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.structures.Detection
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


object WorkerService {

    private lateinit var localDetect: DetectObjects

    private val jobQueue = LinkedBlockingQueue<RunnableJobObjects>()
    private var running = false
    private var executor : ExecutorService = Executors.newSingleThreadExecutor()
    private var waitingResultsMap : HashMap<Int, (List<Detection?>) -> Unit> = HashMap()
    private var server: GRPCServerBase? = null
    private val brokerGRPC = BrokerGRPCClient("127.0.0.1")

    init {
        executor.shutdown()
        ODLogger.logInfo("WorkerService, INIT")
    }

    internal fun queueJob(job: ODProto.Job, callback: ((List<Detection>) -> Unit)?): StatusCode {
        ODLogger.logInfo("WorkerService, QUEUE_JOB, init")
        if (!running) throw Exception("WorkerService not running")
        jobQueue.put(RunnableJobObjects(job, callback))
        WorkerProfiler.atomicOperation(WorkerProfiler.totalJobs, increment = true)
        ODLogger.logInfo("WorkerService, QUEUE_JOB, JOB_QUEUED, JOBS_IN_QUEUE=${WorkerProfiler.totalJobs.get() - WorkerProfiler.runningJobs.get()}")
        return if (callback == null) StatusCode.Success else StatusCode.Waiting
    }

    fun start(localDetect: DetectObjects, useNettyServer: Boolean = false, batteryMonitor: BatteryMonitor? = null) {
        ODLogger.logInfo("WorkerService, START, INIT")
        if (running) return
        if (executor.isShutdown || executor.isTerminated) executor = Executors.newFixedThreadPool(ODSettings.workingThreads)
        this.localDetect = localDetect
        WorkerProfiler.setBatteryMonitor(batteryMonitor)

        server = WorkerGRPCServer(useNettyServer).start()
        WorkerProfiler.start()

        thread(start = true, isDaemon = true, name = "WorkerService") {
            running = true
            while (running) {
                try {
                    if (!(executor.isShutdown || executor.isTerminated) && running) executor.execute(jobQueue.take())
                    else running = false
                    ODLogger.logInfo("WorkerService, START, SEND_FROM_QUEUE_TO_EXECUTOR")
                } catch (e: Exception) { running = false }
            }
            if (!executor.isShutdown) executor.shutdownNow()
        }
        brokerGRPC.announceServiceStatus(ODProto.ServiceStatus.newBuilder().setType(ODProto.ServiceStatus.Type.WORKER).setRunning(true).build()) {
            ODLogger.logInfo("WorkerService, START, RUNNING")
        }
    }

    fun stop(stopGRPCServer: Boolean = true, callback: ((ODProto.Status?) -> Unit)? = null) {
        running = false
        if (stopGRPCServer) server?.stop()
        waitingResultsMap.clear()
        jobQueue.clear()
        jobQueue.offer(RunnableJobObjects(null) {})
        executor.shutdownNow()
        WorkerProfiler.destroy()
        brokerGRPC.announceServiceStatus(ODProto.ServiceStatus.newBuilder().setType(ODProto.ServiceStatus.Type.WORKER).setRunning(false).build()) {S ->
            ODLogger.logInfo("WorkerService, STOP")
            callback?.invoke(S)
        }
    }

    fun monitorBattery() {
        ODLogger.logInfo("WorkerService, MONITOR_BATTERY")
        WorkerProfiler.monitorBattery()
    }

    fun loadModel(model: Model, callback: ((ODProto.Status) -> Unit)? = null) {
        ODLogger.logInfo("WorkerService, LOAD_MODEL")
        if(running) localDetect.loadModel(model, callback)
    }


    fun listModels() : Set<Model> {
        ODLogger.logInfo("WorkerService, LIST_MODELS")
        return localDetect.models.toSet()
    }

    internal fun isRunning() : Boolean { return running }

    fun stopService(callback: ((ODProto.Status?) -> Unit)) {
        ODLogger.logInfo("WorkerService, STOP_SERVICE")
        stop(false) {S -> callback(S)}
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    private class RunnableJobObjects(val job: ODProto.Job?, var callback: ((List<Detection>) -> Unit)?) : Runnable {
        override fun run() {
            ODLogger.logInfo("WorkerService, RUN_JOB, JOB_ID=${job?.id}")
            WorkerProfiler.atomicOperation(WorkerProfiler.runningJobs, increment = true)
            try {
                ODLogger.logInfo("WorkerService, RUN_JOB, START, JOB_ID=${job?.id}")
                WorkerProfiler.profileExecution { callback?.invoke(localDetect.detectObjects(job?.data?.toByteArray() ?: ByteArray(0))) }
                ODLogger.logInfo("WorkerService, RUN_JOB, END, JOB_ID=${job?.id}")
            } catch (e: Exception) {
                ODLogger.logError("WorkerService, RUN_JOB, FAIL, JOB_ID=${job?.id}")
                callback?.invoke(emptyList())
            }
            WorkerProfiler.atomicOperation(WorkerProfiler.runningJobs, WorkerProfiler.totalJobs)
            ODLogger.logInfo("WorkerService, RUN_JOB, COMPLETE, JOB_ID=${job?.id}")
        }
    }
}