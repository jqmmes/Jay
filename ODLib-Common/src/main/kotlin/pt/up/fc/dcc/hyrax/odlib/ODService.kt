package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.ReturnStatus
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


/**
 * TODO: Threadpool to execute jobs
 * TODO: Run as a service
 *
 */

 class ODService(val localDetect: DetectObjects) {

    private val jobQueue = LinkedBlockingQueue<JobObjects>()

    internal fun startService() : ODService{
        thread(start = true, isDaemon = true, name = "ODServiceThread") {
            while (true) {
                val executor = Executors.newFixedThreadPool(5)
                executor.execute(jobQueue.take())
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
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    inner class JobObjects(var imageData: ByteArray, var callback: ODCallback? = null) : Runnable {
        override fun run() {
            localDetect
            if (callback != null) callback!!.onNewResult()
            TODO("else send async result back to client") //To change body of created functions use File | Settings | File Templates.
        }
    }
}