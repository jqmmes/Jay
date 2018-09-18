package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.RemoteODCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.ReturnStatus
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


/**
 *
 */

 class ODService(val localDetect: DetectObjects) {

    private val jobQueue = LinkedBlockingQueue<JobObjects>()
    private var running = false
    private var executor : ExecutorService = Executors.newFixedThreadPool(5)
    private var waitingResultsMap : HashMap<Int, RemoteODCallback> = HashMap()

    internal fun startService() : ODService{
        thread(start = true, isDaemon = true, name = "ODServiceThread") {
            running = true
            while (running) {
                try {
                    executor.execute(jobQueue.take())
                } catch (e: InterruptedException) {
                    running = false
                }
            }
        }
        return this
    }

    internal fun putJob(imgPath: String, callback: ODCallback? = null) : ReturnStatus {
        jobQueue.put(JobObjects(imageData = localDetect.getByteArrayFromImage(imgPath), callback = callback))
        if (callback == null) return ReturnStatus.Success
        return ReturnStatus.Waiting
    }

    internal fun putRemoteJob(jobId: Int, imageData: ByteArray, callback: ODCallback? = null) : ReturnStatus {
        jobQueue.put(JobObjects(jobId, imageData, callback))
        return ReturnStatus.Waiting
    }

    fun stop() {
        running = false
        waitingResultsMap.clear()
        jobQueue.clear()
        executor.shutdown()
    }

    fun newRemoteResultAvailable(jobId: Int, detections: List<ODUtils.ODDetection?>) {
        if (waitingResultsMap.containsKey(jobId)) {
            waitingResultsMap[jobId]!!.onNewResult()
            waitingResultsMap.remove(jobId)
        }
    }

    fun waitResultsForTask(jobId: Int, callback: RemoteODCallback) {
        waitingResultsMap[jobId] = callback
    }

    inner class JobObjects(var jobId: Int? = null, var imageData: ByteArray, var callback: ODCallback? = null) : Runnable {
        override fun run() {
            localDetect.detectObjects(imageData)
            if (callback != null) callback!!.onNewResult()
            TODO("else send async result back to client") //To change body of created functions use File | Settings | File Templates.
        }
    }
}