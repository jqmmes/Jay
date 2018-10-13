package pt.up.fc.dcc.hyrax.odlib.jobManager

import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.scheduler.LocalScheduler
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger

object JobManager {
    private lateinit var jobWarehouse : JobWarehouse
    private var init = false
    private var jobId = 0L

    fun createJob(data: ByteArray) : ODJob {
        return ODJob(jobId++, data)
    }

    internal fun createWarehouse(scheduler: Scheduler = LocalScheduler()) {
        if (init) return
        ODLogger.logInfo("JobManager creating job WareHouse")
        jobWarehouse = JobWarehouse(scheduler)
        init = true
    }

    fun getScheduler(): Scheduler {
        return jobWarehouse.getScheduler()
    }

    fun destroyWarehouse() {
        if (!init) return
        jobWarehouse.stop()
        init = false
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