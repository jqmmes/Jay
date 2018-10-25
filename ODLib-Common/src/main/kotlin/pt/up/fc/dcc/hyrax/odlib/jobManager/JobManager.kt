package pt.up.fc.dcc.hyrax.odlib.jobManager

import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread

object JobManager {
    private var jobId = 0L
    private var jobResultsCallback : ((Long, List<ODUtils.ODDetection?>) -> Unit)? = null
    private val pendingJobs = LinkedBlockingDeque<ODJob>()
    private var running: Boolean = false
    private lateinit var scheduler: Scheduler

    fun startService(scheduler: Scheduler) {
        this.scheduler = scheduler
        thread (isDaemon = true, name="JobManagerService") {
            ODLogger.logInfo("JobManager Service starting thread...")
            var job: ODJob
            running = true
            while(running) {
                ODLogger.logInfo("JobManager Service waiting for jobs..")
                job = pendingJobs.takeFirst()
                if (running) { scheduler.scheduleJob(job) }
            }
        }
    }

    internal fun stopService() {
        running = false
        pendingJobs.clear()
        pendingJobs.offerFirst(ODJob(Long.MAX_VALUE,ByteArray(0)))
        scheduler.destroy()
    }

    fun createJob(data: ByteArray) : ODJob {
        return ODJob(jobId++, data)
    }

    fun addResultsCallback(callback: (Long, List<ODUtils.ODDetection?>) -> Unit) {
        jobResultsCallback = callback
    }

    fun addJob(job: ODJob) {
        pendingJobs.putLast(job)
    }

    internal fun addResults(jobId: Long, results: List<ODUtils.ODDetection?>) {
        if (jobResultsCallback != null) jobResultsCallback!!(jobId, results)
    }
}