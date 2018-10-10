package pt.up.fc.dcc.hyrax.odlib.jobManager

import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.scheduler.LocalScheduler
import pt.up.fc.dcc.hyrax.odlib.scheduler.Scheduler

object JobManager {
    private lateinit var jobWarehouse : JobWarehouse
    private var init = false
    private var jobId = 0L

    fun createJob(data: ByteArray) : ODJob {
        return ODJob(jobId++, data)
    }

    fun createWarehouse(scheduler: Scheduler = LocalScheduler()) {
        if (init) return
        jobWarehouse = JobWarehouse(scheduler)
    }

    fun destroyWarehouse() {
        if (!init) return
        jobWarehouse.stop()
    }

    fun addJob(job: ODJob) : Boolean{
        if (!init) return false
        jobWarehouse.addJob(job)
        return true
    }

    internal fun addResults(jobId: Long, results: List<ODUtils.ODDetection?>) {
        jobWarehouse.addResults(jobId, results)
    }
}