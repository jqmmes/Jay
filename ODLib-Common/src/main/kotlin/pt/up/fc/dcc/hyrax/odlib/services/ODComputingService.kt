package pt.up.fc.dcc.hyrax.odlib.services

import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.enums.ReturnStatus
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

object ODComputingService {
    private val jobQueue = LinkedBlockingQueue<RunnableJobObjects>()
    var requestId : AtomicInteger = AtomicInteger(0)
    private var running = false
    private var executor = Executors.newFixedThreadPool(5)
    private var waitingResultsMap : HashMap<Int, (List<ODUtils.ODDetection?>) -> Unit> = HashMap()
    private var runningJobs : AtomicInteger = AtomicInteger(0)
    lateinit var localDetect: DetectObjects

    fun isRunning() : Boolean{
        return running
    }

    internal fun putJobAndWait(imgPath: String) : List<ODUtils.ODDetection?> {
        if (!running) throw Exception("ODComputingService not running")
        val future = executor.submit(CallableJobObjects(localDetect, imageData = localDetect.getByteArrayFromImage(imgPath)))
        return future.get() //wait termination
    }

    internal fun putJob(imgPath: String, callback: ((List<ODUtils.ODDetection>) -> Unit)?) : ReturnStatus {
        return putJob(localDetect.getByteArrayFromImage(imgPath), callback)
    }

    internal fun putJob(imgData: ByteArray, callback: ((List<ODUtils.ODDetection>) -> Unit)?) : ReturnStatus {
        if (!running) throw Exception("ODComputingService not running")
        jobQueue.put(RunnableJobObjects(localDetect, imageData = imgData, callback = callback))
        if (callback == null) return ReturnStatus.Success
        return ReturnStatus.Waiting
    }

    fun startService(localDetect: DetectObjects) {
        if (running) return
        ODComputingService.localDetect = localDetect
        thread(start = true, isDaemon = true, name = "ODComputingService") {
            running = true
            while (running) {
                try {
                    executor.execute(jobQueue.take())
                } catch (e: InterruptedException) {
                    running = false
                }
            }
        }
    }

    fun stop() {
        running = false
        waitingResultsMap.clear()
        jobQueue.clear()
        executor.shutdown()
    }

    fun getJobsRunningCount() : Int {
        return runningJobs.get()
    }

    fun getPendingJobsCount() : Int {
        return jobQueue.count()
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
            runningJobs.incrementAndGet()
            if (callback != null) callback!!(localDetect.detectObjects(imageData))
            runningJobs.decrementAndGet()
        }
    }
}