package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODCallback
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

    internal fun putJob() : ReturnStatus{
        jobQueue.put(JobObjects(ByteArray(0)))
        return ReturnStatus.Success
    }

    internal fun putJobAndWay(callback : ODCallback) {
        jobQueue.put(JobObjects(ByteArray(0), callback))
    }

    fun stop() {
        running = false
    }

    inner class JobObjects(var imageData: ByteArray, var callback: ODCallback? = null) : Runnable {
        override fun run() {
            localDetect
            if (callback != null) callback!!.onNewResult()
            TODO("else send async result back to client") //To change body of created functions use File | Settings | File Templates.
        }
    }
}