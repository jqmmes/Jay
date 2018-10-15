package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.jobManager.JobManager
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger

abstract class Scheduler {
    protected open fun jobCompleted(id: Long, results: List<ODUtils.ODDetection?>) {
        ODLogger.logInfo("Job ($id) completed\n\t\t$results")
        JobManager.addResults(id, results)
    }

    abstract fun scheduleJob(job: ODJob)
}