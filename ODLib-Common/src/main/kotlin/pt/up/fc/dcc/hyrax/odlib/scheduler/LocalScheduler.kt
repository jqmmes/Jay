package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.interfaces.JobResultCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.jobManager.JobManager
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob

class LocalScheduler : Scheduler() {
    private val executingJobs = HashMap<Long, ODJob>()

    init {
        ODLogger.logInfo("Starting LocalScheduler...")
    }

    override fun scheduleJob(job: ODJob) {
        ODLogger.logInfo("LocalScheduler schedule Job ${job.getId()}")
        ClientManager.getLocalODClient().asyncDetectObjects(job) {R -> jobCompleted(job.getId(), R)}
        executingJobs[job.getId()] = job
    }
}