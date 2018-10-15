package pt.up.fc.dcc.hyrax.odlib.jobManager

import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread

internal class JobWarehouse (private val scheduler: Scheduler){
    private val pendingJobs = LinkedBlockingDeque<ODJob>()
    private var running: Boolean = false

    init {
        thread (isDaemon = true, name="JobWarehouse") {
            ODLogger.logInfo("JobWarehouse starting thread...")
            var job: ODJob
            running = true
            while(running) {
                ODLogger.logInfo("JobWarehouse waiting for jobs..")
                job = pendingJobs.takeFirst()
                if (running) {
                    scheduler.scheduleJob(job)
                }
            }
        }
    }

    internal fun getScheduler() : Scheduler {
        return scheduler
    }

    internal fun addJob(job: ODJob) {
        ODLogger.logInfo("JobWarehouse addJob ${job.getId()}")
        pendingJobs.putLast(job)
        ODLogger.logInfo("JobWarehouse addJob ${job.getId()}... added")
    }

    internal fun stop() {
        running = false
        pendingJobs.clear()
        pendingJobs.offer(ODJob(Long.MAX_VALUE,ByteArray(0)))
    }
}