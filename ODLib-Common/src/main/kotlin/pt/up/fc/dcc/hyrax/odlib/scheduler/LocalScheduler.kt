package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.multicast.MulticastAdvertiser

class LocalScheduler : Scheduler() {
    override fun destroy() {
        executingJobs.clear()
    }

    private val executingJobs = HashMap<Long, ODJob>()

    init {
        ODLogger.logInfo("Starting LocalScheduler...")
        MulticastAdvertiser.setAdvertiseData(1)
    }

    override fun scheduleJob(job: ODJob) {
        ODLogger.logInfo("LocalScheduler schedule Job ${job.getId()}")
        ClientManager.getLocalODClient().asyncDetectObjects(job) {R -> jobCompleted(job.getId(), R)}
        executingJobs[job.getId()] = job
    }
}