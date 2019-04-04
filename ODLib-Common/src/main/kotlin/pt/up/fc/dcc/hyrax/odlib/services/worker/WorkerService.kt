package pt.up.fc.dcc.hyrax.odlib.services.worker

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.StatusCode
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.worker.grpc.WorkerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.structures.ODDetection
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.structures.ODModel
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
    private var waitingResultsMap : HashMap<Int, (List<ODDetection?>) -> Unit> = HashMap()
    private var server: GRPCServerBase? = null


    init {
        executor.shutdown()
        ODLogger.logInfo("WorkerService init")
    }

    internal fun queueJob(job: ODProto.Job, callback: ((List<ODDetection>) -> Unit)?): StatusCode {
        if (!running) throw Exception("WorkerService not running")
        jobQueue.put(RunnableJobObjects(job, callback))
        WorkerProfiler.atomicOperation(WorkerProfiler.totalJobs, increment = true)
        return if (callback == null) StatusCode.Success else StatusCode.Waiting
    }

    fun start(localDetect: DetectObjects, useNettyServer: Boolean = false) {
        if (running) return
        if (executor.isShutdown || executor.isTerminated) executor = Executors.newFixedThreadPool(ODSettings.workingThreads)
        this.localDetect = localDetect

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
    }

    fun stop() {
        running = false
        server?.stop()
        waitingResultsMap.clear()
        jobQueue.clear()
        jobQueue.offer(RunnableJobObjects(null) {})
        executor.shutdownNow()
        WorkerProfiler.destroy()
    }

    fun loadModel(odModel: ODModel, callback: ((ODProto.Status) -> Unit)? = null) {
        if(running) localDetect.loadModel(odModel, callback)
    }


    fun listModels() : Set<ODModel> {
        return localDetect.models.toSet()
    }

    private class RunnableJobObjects(val job: ODProto.Job?, var callback: ((List<ODDetection>) -> Unit)?) : Runnable {
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