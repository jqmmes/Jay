package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread

abstract class Scheduler {

    protected open fun jobCompleted(id: Long, results: List<ODDetection?>) {
        ODLogger.logInfo("Job_Complete\t$id\n\t\t$results")
        addResults(id, results)
    }

    abstract fun scheduleJob(job: ODJob)
    abstract fun destroy()

    companion object {
        private var jobId = 0L
        private var jobResultsCallback : ((Long, List<ODDetection?>) -> Unit)? = null
        private val pendingJobs = LinkedBlockingDeque<ODJob>()
        private var running: Boolean = false
        private lateinit var scheduler: Scheduler

        fun startService(scheduler: Scheduler) {

            Companion.scheduler = scheduler
            thread (isDaemon = true, name="JobManagerService") {
                ODLogger.logInfo("Scheduler Service starting thread...")
                var job: ODJob
                running = true
                while(running) {
                    ODLogger.logInfo("Scheduler Service waiting for jobs..")
                    job = pendingJobs.takeFirst()
                    if (running) { scheduler.scheduleJob(job) }
                }
            }
        }

        internal fun stopService() {
            running = false
            pendingJobs.clear()
            pendingJobs.offerFirst(ODJob(Long.MAX_VALUE, ByteArray(0)))
            scheduler.destroy()
        }

        fun createJob(data: ByteArray) : ODJob {
            return ODJob(jobId++, data)
        }

        fun addResultsCallback(callback: (jobID: Long, results: List<ODDetection?>) -> Unit) {
            jobResultsCallback = callback
        }

        fun addJob(job: ODJob): Long {
            return try {
                pendingJobs.putLast(job)
                job.getId()
            } catch (ignore: Exception) {
                -1L
            }
        }

        internal fun addResults(jobId: Long, results: List<ODDetection?>) {
            if (jobResultsCallback != null) jobResultsCallback!!(jobId, results)
        }
    }
}