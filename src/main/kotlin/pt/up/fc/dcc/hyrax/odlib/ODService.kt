package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.tensorflow.cloudletDetectObjects
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


/**
 * TODO: Threadpool to execute jobs
 * TODO: Run as a service
 *
 */

 class ODService {

    private val jobQueue = LinkedBlockingQueue<JobObjects>()

    protected fun startService() {
        thread(start = true, isDaemon = true, name = "ODServiceThread") {
            while (true) {
                val executor = Executors.newFixedThreadPool(5)
                executor.execute(jobQueue.take())
            }
        }
    }

    internal fun putJob(){
        jobQueue.put(JobObjects(ByteArray(0)))
    }

    inner class JobObjects(imageData: ByteArray) : Runnable {
        override fun run() {
            cloudletDetectObjects()
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}