package pt.up.fc.dcc.hyrax.odlib.services.worker

import org.apache.commons.collections4.queue.CircularFifoQueue
import pt.up.fc.dcc.hyrax.odlib.enums.ReturnStatus
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.broker.grpc.BrokerGRPCClient
import pt.up.fc.dcc.hyrax.odlib.services.worker.grpc.WorkerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.services.worker.status.StatusManager
import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * TODO: Cleanup
 */

object WorkerService {
    private val jobQueue = LinkedBlockingQueue<RunnableJobObjects>()
    private var running = false
    private var workingThreads = 1
    private var executor : ExecutorService = Executors.newSingleThreadExecutor()
    private var waitingResultsMap : HashMap<Int, (List<ODDetection?>) -> Unit> = HashMap()
    private var runningJobs : AtomicInteger = AtomicInteger(0)
    private var totalJobs : AtomicInteger = AtomicInteger(0)
    private var queueSize : Int = Int.MAX_VALUE
    private lateinit var localDetect: DetectObjects
    private val JOBS_LOCK = Object()
    private var server: GRPCServerBase? = null
    private var brokerGRPC = BrokerGRPCClient("127.0.0.1")
    private val averageComputationTimes = CircularFifoQueue<Long>(ODSettings.averageComputationTimesToStore)

    private fun getProto(): ODProto.Worker? {
        val builder = ODProto.Worker.newBuilder()
        builder.queueSize = queueSize
        builder.runningJobs = runningJobs.get()
        builder.avgTimePerJob = if (averageComputationTimes.size > 0) averageComputationTimes.sum()/averageComputationTimes.size else 0
        return builder.build()
    }

    init {
        executor.shutdown()
        StatusManager.setCpuWorkers(workingThreads)
        StatusManager.setQueueSize(queueSize)
        ODLogger.logInfo("WorkerService init")
    }

    fun setWorkingThreads(threadCount: Int) {
        workingThreads = threadCount
        StatusManager.setCpuWorkers(workingThreads)
        ODLogger.logInfo("Set new threadPool thread number $threadCount. Will take effect next time service is started")
    }

    fun isRunning() : Boolean{
        return running
    }

    internal fun queueJob(job: ODProto.Job, callback: ((List<ODDetection>) -> Unit)?): ReturnStatus {
        ODLogger.logInfo("WorkerService put job into Job Queue...")
        println("WorkerService put job into Job Queue...")
        if (!running) throw Exception("WorkerService not running")
        jobQueue.put(RunnableJobObjects(job, callback))
        synchronized(JOBS_LOCK) {
            totalJobs.incrementAndGet()
            brokerGRPC.diffuseWorkerStatus(getProto())
            StatusManager.setIdleJobs(totalJobs.get()- runningJobs.get())
        }
        return if (callback == null) ReturnStatus.Success else ReturnStatus.Waiting
    }

    fun start(localDetect: DetectObjects, useNettyServer: Boolean = false) {
        server = WorkerGRPCServer(useNettyServer).start()
        brokerGRPC.announceMulticast()

        if (running) return
        if (executor.isShutdown || executor.isTerminated) executor = Executors.newFixedThreadPool(workingThreads)
        this.localDetect = localDetect
        thread(start = true, isDaemon = true, name = "WorkerService") {
            running = true
            while (running) {
                try {
                    ODLogger.logInfo("WorkerService waiting for job...")
                    val task = jobQueue.take()
                    if (!(executor.isShutdown || executor.isTerminated) && running) executor.execute(task)
                    else running = false
                    ODLogger.logInfo("WorkerService job submitted to thread pool")
                } catch (e: Exception) {
                    ODLogger.logWarn("WorkerService error")
                    running = false
                }
            }
            if (!executor.isShutdown) executor.shutdownNow()
        }

        //loadModel(listModels().first())
    }

    fun stop() {
        running = false
        server?.stop()
        waitingResultsMap.clear()
        jobQueue.clear()
        jobQueue.offer(RunnableJobObjects(null, {}))
        executor.shutdownNow()
    }

    fun getJobsRunningCount() : Int {
        return runningJobs.get()
    }

    fun getPendingJobsCount() : Int {
        synchronized(JOBS_LOCK) {
            return totalJobs.get() - runningJobs.get()
        }
    }

    fun getWorkingThreads() : Int {
        return workingThreads
    }

    fun getQueueSize(): Int {
        return queueSize
    }

    fun loadModel(odModel: ODModel, callback: ((ODProto.Status) -> Unit)? = null) {
        println("Loading Model $odModel")
        if(running) localDetect.loadModel(odModel, callback)
    }


    fun listModels() : Set<ODModel> {
        return localDetect.models.toSet()
    }

    private class RunnableJobObjects(val job: ODProto.Job?, var callback: ((List<ODDetection>) -> Unit)?) : Runnable {
        override fun run() {
            val data = job?.data?.toByteArray() ?: ByteArray(0)
            val id = job?.id ?: -1
            synchronized(JOBS_LOCK) {
                runningJobs.incrementAndGet()
                StatusManager.setRunningJobs(runningJobs.get())
            }
            ODLogger.logInfo("Running_Job\t$id")
            try {
                val computationStartTimestamp = System.currentTimeMillis()
                callback?.invoke(localDetect.detectObjects(data))
                averageComputationTimes.add(System.currentTimeMillis()-computationStartTimestamp)
                ODLogger.logInfo("Finished_Running_Job\t$id")
            } catch (e: Exception) {
                ODLogger.logError("Execution_Failed ${e.stackTrace}")
                e.printStackTrace()
                callback?.invoke(emptyList())
                ODLogger.logInfo("Error_Running_Job\t$id")
            }
            synchronized(JOBS_LOCK) {
                runningJobs.decrementAndGet()
                totalJobs.decrementAndGet()
                StatusManager.setRunningJobs(runningJobs.get())
                StatusManager.setIdleJobs(totalJobs.get()- runningJobs.get())
            }
        }
    }
}