package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClient
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.RemoteODCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.ReturnStatus
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


/**
 *
 */

 class ODService {

    companion object {
        private val jobQueue = LinkedBlockingQueue<RunnableJobObjects>()
        private var requestId = 0
        private var running = false
        private var executor = Executors.newFixedThreadPool(5)
        private var waitingResultsMap : HashMap<Int, RemoteODCallback> = HashMap()
        private lateinit var localDetect: DetectObjects

        fun isRunning() : Boolean{
            return running
        }

        internal fun putJobAndWait(imgPath: String) : List<ODUtils.ODDetection?> {
            if (!running) throw Exception("ODService not running")
            //localDetect.detectObjects(imgPath)
            //return emptyList()
            val future = executor.submit(CallableJobObjects(localDetect, imageData = localDetect.getByteArrayFromImage(imgPath)))
            return future.get() //wait termination
        }

        internal fun putJob(imgPath: String, callback: ODCallback? = null) : ReturnStatus {
            return putJob(localDetect.getByteArrayFromImage(imgPath), callback)
        }

        internal fun putJob(imgData: ByteArray, callback: ODCallback?) : ReturnStatus{
            if (!running) throw Exception("ODService not running")
            jobQueue.put(RunnableJobObjects(localDetect, imageData = imgData, callback = callback))
            if (callback == null) return ReturnStatus.Success
            return ReturnStatus.Waiting
        }

        internal fun putRemoteJob(remoteClient: GRPCClient, imgPath: String) : List<ODUtils.ODDetection?>{
            return ODUtils.parseResults(remoteClient.putJobSync(requestId++, localDetect.getByteArrayFromImage(imgPath)))
        }

        /*internal fun putRemoteJob(remoteClient: GRPCClient, imgPath: String, callback: ODCallback?) : ReturnStatus {

            return ReturnStatus.Waiting
        }*/

        internal fun newRemoteResultAvailable(jobId: Int, detections: List<ODUtils.ODDetection?>) {
            if (waitingResultsMap.containsKey(jobId)) {
                waitingResultsMap[jobId]!!.onNewResult(detections)
                waitingResultsMap.remove(jobId)
            }
        }

        fun waitResultsForTask(jobId: Int, callback: RemoteODCallback) {
            waitingResultsMap[jobId] = callback
        }

        fun startService(localDetect: DetectObjects) {
            if (running) return
            this.localDetect = localDetect
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
        }

        fun stop() {
            running = false
            waitingResultsMap.clear()
            jobQueue.clear()
            executor.shutdown()
        }
    }

    private class CallableJobObjects(val localDetect: DetectObjects, var jobId: Int? = null, var imageData: ByteArray, var callback: ODCallback? = null) : Callable<List<ODUtils.ODDetection>> {
        override fun call(): List<ODUtils.ODDetection> {
            localDetect.detectObjects(imageData)
            return emptyList()
        }
    }

    private class RunnableJobObjects(val localDetect: DetectObjects, var jobId: Int? = null, var imageData: ByteArray, var callback: ODCallback? = null) : Runnable {
        override fun run() {
            localDetect.detectObjects(imageData)
            if (callback != null) callback!!.onNewResult(emptyList())
        }
    }
}