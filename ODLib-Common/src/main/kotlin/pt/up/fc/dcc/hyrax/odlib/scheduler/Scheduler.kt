package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob

interface Scheduler {
    fun jobCompleted(id: Long, results: List<ODUtils.ODDetection?>)
    fun scheduleJob(job: ODJob)
}