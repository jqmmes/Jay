package pt.up.fc.dcc.hyrax.odlib.jobManager

import pt.up.fc.dcc.hyrax.odlib.ODUtils
import pt.up.fc.dcc.hyrax.odlib.scheduler.Scheduler
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread

internal class JobWarehouse (val scheduler: Scheduler){
    private val pendingJobs = LinkedBlockingDeque<ODJob>()
    private var running: Boolean = false

    init {
        thread (isDaemon = true) {
            var job: ODJob
            running = true
            while(running) {
                job = pendingJobs.takeFirst()
                if (running) {
                    scheduler.scheduleJob(job)
                }
            }
        }
    }

    internal fun addResults(jobId: Long, results: List<ODUtils.ODDetection?>) {
        scheduler.jobCompleted(jobId, results)
    }

    internal fun addJob(job: ODJob) {
        pendingJobs.putLast(job)
    }

    internal fun stop() {
        running = false
        pendingJobs.clear()
        pendingJobs.offer(null)
    }
}