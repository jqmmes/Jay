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
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
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
        ODLogger.logInfo("WorkerService init")
    }

    internal fun queueJob(job: ODProto.Job, callback: ((List<Detection>) -> Unit)?): StatusCode {
        if (!running) throw Exception("WorkerService not running")
        jobQueue.put(RunnableJobObjects(job, callback))
        WorkerProfiler.atomicOperation(WorkerProfiler.totalJobs, increment = true)
        return if (callback == null) StatusCode.Success else StatusCode.Waiting
    }

    fun start(localDetect: DetectObjects, useNettyServer: Boolean = false, batteryMonitor: BatteryMonitor? = null) {
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
                } catch (e: Exception) { running = false }
            }
            if (!executor.isShutdown) executor.shutdownNow()
        }
        brokerGRPC.announceServiceStatus(ODProto.ServiceStatus.newBuilder().setType(ODProto.ServiceStatus.Type.WORKER).setRunning(true).build()) {
            ODLogger.logInfo("WorkerService Running")
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
            ODLogger.logInfo("WorkerService Stopped")
            callback?.invoke(S)
        }
    }

    fun monitorBattery() {
        println("Monitoring Battery")
        WorkerProfiler.monitorBattery()
    }

    fun loadModel(model: Model, callback: ((ODProto.Status) -> Unit)? = null) {
        println("WorkerService::loadModel ...")
        if(running) localDetect.loadModel(model, callback)
    }


    fun listModels() : Set<Model> {
        return localDetect.models.toSet()
    }

    internal fun isRunning() : Boolean { return running }

    fun stopService(callback: ((ODProto.Status?) -> Unit)) {
        stop(false) {S -> callback(S)}
    }

    fun stopServer() {
        server?.stopNowAndWait()
    }

    private class RunnableJobObjects(val job: ODProto.Job?, var callback: ((List<Detection>) -> Unit)?) : Runnable {
        override fun run() {
            WorkerProfiler.atomicOperation(WorkerProfiler.runningJobs, increment = true)
            try {
                WorkerProfiler.profileExecution { callback?.invoke(localDetect.detectObjects(job?.data?.toByteArray() ?: ByteArray(0))) }
            } catch (e: Exception) {
                ODLogger.logError("Execution_Failed ${e.stackTrace}")
                callback?.invoke(emptyList())
            }
            WorkerProfiler.atomicOperation(WorkerProfiler.runningJobs, WorkerProfiler.totalJobs)
        }
    }
}