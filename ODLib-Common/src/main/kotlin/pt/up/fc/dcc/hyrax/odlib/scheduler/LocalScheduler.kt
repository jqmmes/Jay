package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob

open class LocalScheduler : Scheduler {

    private val executingJobs = HashMap<Long, ODJob>()

    override fun jobCompleted(id: Long, results: List<ODUtils.ODDetection?>) {
        ODLogger.logInfo("Job ($id) completed\n\t\t$results")
    }

    override fun scheduleJob(job: ODJob) {
        ClientManager.getLocalODClient().asyncDetectObjects(job) {R -> jobCompleted(job.getId(), R)}
        executingJobs[job.getId()] = job
    }
}