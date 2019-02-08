package pt.up.fc.dcc.hyrax.odlib.services.worker

import pt.up.fc.dcc.hyrax.odlib.enums.ReturnStatus
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServerBase
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.services.worker.grpc.WorkerGRPCServer
import pt.up.fc.dcc.hyrax.odlib.status.StatusManager
import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

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

    internal fun putJobAndWait(odJob: ODJob) : List<ODDetection?> {
        ODLogger.logInfo("WorkerService put job and wait..")
        if (!running) throw Exception("WorkerService not running")
        val future = executor.submit(CallableJobObjects(localDetect, odJob))
        synchronized(JOBS_LOCK) {
            totalJobs.incrementAndGet()
            StatusManager.setIdleJobs(totalJobs.get()- runningJobs.get())
        }
        return future.get() //wait termination
    }

    internal fun putJob(imgData: ByteArray, callback: ((List<ODDetection>) -> Unit)?, jobId: String): ReturnStatus {
        ODLogger.logInfo("WorkerService put job into Job Queue...")
        if (!running) throw Exception("WorkerService not running")
        jobQueue.put(RunnableJobObjects(localDetect, imageData = imgData, callback = callback, jobId = jobId))
        synchronized(JOBS_LOCK) {
            totalJobs.incrementAndGet()
            StatusManager.setIdleJobs(totalJobs.get()- runningJobs.get())
        }
        if (callback == null) return ReturnStatus.Success
        return ReturnStatus.Waiting
    }

    fun start(localDetect: DetectObjects, useNettyServer: Boolean = false) {
        server = WorkerGRPCServer(useNettyServer).start()

        if (running) return
        if (executor.isShutdown || executor.isTerminated) executor = Executors.newFixedThreadPool(workingThreads)
        WorkerService.localDetect = localDetect
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
    }

    fun stop() {
        running = false
        if (server != null) server!!.stop()
        waitingResultsMap.clear()
        jobQueue.clear()
        jobQueue.offer(RunnableJobObjects(localDetect, ByteArray(0), {}))
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

    fun loadModel(odModel: ODModel) {
        if(running) localDetect.loadModel(odModel)
    }

    fun modelLoaded(odModel: ODModel): Boolean {
        if (running) return localDetect.modelLoaded(odModel)
        return false
    }

    fun configTFModel(configRequest: Pair<ODModel, HashMap<String, String>>) {
        for (key in configRequest.second.keys) {
            when (key) {
                "minScore" -> localDetect.minimumScore = configRequest.second[key]!!.toFloat()
            }
        }
    }

    fun listModels() : Set<ODModel> {
        return localDetect.models.toSet()
    }

    private class CallableJobObjects(val localDetect: DetectObjects, val odJob: ODJob) : Callable<List<ODDetection>> {
        override fun call(): List<ODDetection> {
            synchronized(JOBS_LOCK) {
                runningJobs.incrementAndGet()
                StatusManager.setRunningJobs(runningJobs.get())
            }
            var result = emptyList<ODDetection>()
            try {
                result = localDetect.detectObjects(odJob.getData())
            } catch (e: Exception) {
                ODLogger.logError("Execution_Failed ${e.stackTrace}")
            }
            synchronized(JOBS_LOCK) {
                runningJobs.decrementAndGet()
                totalJobs.decrementAndGet()
                StatusManager.setRunningJobs(runningJobs.get())
                StatusManager.setIdleJobs(totalJobs.get()- runningJobs.get())
            }
            return result
        }
    }

    private class RunnableJobObjects(val localDetect: DetectObjects, var imageData: ByteArray, var callback: (
    (List<ODDetection>) -> Unit)?, val jobId: String? = null) : Runnable {
        override fun run() {
            synchronized(JOBS_LOCK) {
                runningJobs.incrementAndGet()
                StatusManager.setRunningJobs(runningJobs.get())
            }
            ODLogger.logInfo("Running_Job\t$jobId")
            try {
                if (callback != null) callback!!(localDetect.detectObjects(imageData))
                ODLogger.logInfo("Finished_Running_Job\t$jobId")
            } catch (e: Exception) {
                ODLogger.logError("Execution_Failed ${e.stackTrace}")
                e.printStackTrace()
                if (callback != null) callback!!(emptyList())
                ODLogger.logInfo("Error_Running_Job\t$jobId")
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