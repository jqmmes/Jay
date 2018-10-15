package pt.up.fc.dcc.hyrax.odlib.services

import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.enums.ReturnStatus
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

object ODComputingService {
    private val jobQueue = LinkedBlockingQueue<RunnableJobObjects>()
    var requestId : AtomicInteger = AtomicInteger(0)
    private var running = false
    private var workingThreads = 1
    private var executor : ExecutorService = Executors.newSingleThreadExecutor()
    private var waitingResultsMap : HashMap<Int, (List<ODUtils.ODDetection?>) -> Unit> = HashMap()
    private var runningJobs : AtomicInteger = AtomicInteger(0)
    private var totalJobs : AtomicInteger = AtomicInteger(0)
    private var queueSize : Int = Int.MAX_VALUE
    lateinit var localDetect: DetectObjects
    private val JOBS_LOCK = Object()

    init {
        executor.shutdown()
        ODLogger.logInfo("ODComputingService init")
    }

    fun setWorkingThreads(threadCount: Int) {
        this.workingThreads = threadCount
        ODLogger.logInfo("Set new threadPool thread number $threadCount. Will take effect next time service is started")
    }

    fun isRunning() : Boolean{
        return running
    }

    internal fun putJobAndWait(imgPath: String) : List<ODUtils.ODDetection?> {
        ODLogger.logInfo("ODComputingService put job and wait..")
        if (!running) throw Exception("ODComputingService not running")
        val future = executor.submit(CallableJobObjects(localDetect, imageData = localDetect.getByteArrayFromImage(imgPath)))
        totalJobs.incrementAndGet()
        return future.get() //wait termination
    }

    internal fun putJob(imgPath: String, callback: ((List<ODUtils.ODDetection>) -> Unit)?) : ReturnStatus {
        return putJob(localDetect.getByteArrayFromImage(imgPath), callback)
    }

    internal fun putJob(imgData: ByteArray, callback: ((List<ODUtils.ODDetection>) -> Unit)?) : ReturnStatus {
        ODLogger.logInfo("ODComputingService put job into Job Queue...")
        if (!running) throw Exception("ODComputingService not running")
        jobQueue.put(RunnableJobObjects(localDetect, imageData = imgData, callback = callback))
        totalJobs.incrementAndGet()
        if (callback == null) return ReturnStatus.Success
        return ReturnStatus.Waiting
    }

    fun startService(localDetect: DetectObjects) {
        if (running) return
        if (executor.isShutdown || executor.isTerminated) executor = Executors.newFixedThreadPool(workingThreads)
        ODComputingService.localDetect = localDetect
        thread(start = true, isDaemon = true, name = "ODComputingService") {
            running = true
            while (running) {
                try {
                    ODLogger.logInfo("ODComputingService waiting for job...")
                    val task = jobQueue.take()
                    if (!(executor.isShutdown || executor.isTerminated) && running) executor.execute(task)
                    else running = false
                    ODLogger.logInfo("ODComputingService job submitted to thread pool")
                } catch (e: Exception) {
                    ODLogger.logWarn("ODComputingService error")
                    running = false
                }
            }
            if (!executor.isShutdown) executor.shutdownNow()
        }
    }

    fun stop() {
        running = false
        waitingResultsMap.clear()
        jobQueue.clear()
        jobQueue.offer(RunnableJobObjects(localDetect, ByteArray(0)) {})
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

    private class CallableJobObjects(val localDetect: DetectObjects, var imageData: ByteArray) : Callable<List<ODUtils.ODDetection>> {
        override fun call(): List<ODUtils.ODDetection> {
            runningJobs.incrementAndGet()
            val result = localDetect.detectObjects(imageData)
            runningJobs.decrementAndGet()
            return result
        }
    }

    private class RunnableJobObjects(val localDetect: DetectObjects, var imageData: ByteArray, var callback: ((List<ODUtils.ODDetection>) -> Unit)?) : Runnable {
        override fun run() {
            ODLogger.logInfo("ODComputingService executing a job")
            runningJobs.incrementAndGet()
            if (callback != null) callback!!(localDetect.detectObjects(imageData))
            ODLogger.logInfo("ODComputingService waiting to decrement and get")
            synchronized(JOBS_LOCK) {
                runningJobs.decrementAndGet()
                totalJobs.decrementAndGet()
            }
            ODLogger.logInfo("ODComputingService finished executing a job")
        }
    }
}