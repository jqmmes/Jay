package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread

abstract class SchedulerBase(name: String) : Scheduler(name) {

    protected open fun jobCompleted(id: String, results: List<ODDetection?>) {
        ODLogger.logInfo("Job_Complete\t$id\n\t\t$results")
        //addResults(id, results)
    }

    abstract fun scheduleJob(job: ODJob)
    abstract fun destroy()

    /*companion object {
        private var jobResultsCallback : ((String, List<ODDetection?>) -> Unit)? = null
        private val pendingJobs = LinkedBlockingDeque<ODJob>()
        private var running: Boolean = false
        private lateinit var scheduler: SchedulerBase

        fun startService(scheduler: SchedulerBase) {

            Companion.scheduler = scheduler
            thread (isDaemon = true, name="JobManagerService") {
                ODLogger.logInfo("SchedulerBase Service starting thread...")
                var job: ODJob
                running = true
                while(running) {
                    ODLogger.logInfo("SchedulerBase Service waiting for jobs..")
                    job = pendingJobs.takeFirst()
                    if (running) { scheduler.scheduleJob(job) }
                }
            }
        }

        internal fun stopService() {
            running = false
            pendingJobs.clear()
            pendingJobs.offerFirst(ODJob(ByteArray(0)))
            scheduler.destroy()
        }

        fun createJob(data: ByteArray) : ODJob {
            return ODJob(data)
        }

        fun addResultsCallback(callback: (jobID: String, results: List<ODDetection?>) -> Unit) {
            jobResultsCallback = callback
        }

        fun addJob(job: ODJob): String? {
            return try {
                pendingJobs.putLast(job)
                job.id
            } catch (ignore: Exception) {
                null
            }
        }

        internal fun addResults(jobId: String, results: List<ODDetection?>) {
            if (jobResultsCallback != null) jobResultsCallback!!(jobId, results)
        }
    }*/
}