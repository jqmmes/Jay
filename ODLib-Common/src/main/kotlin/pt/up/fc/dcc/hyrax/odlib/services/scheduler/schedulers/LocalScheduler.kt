package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.services.broker.multicast.MulticastAdvertiser
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger

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
        ODLogger.logInfo("Job_Scheduled\t${job.getId()}\t${ClientManager.getLocalODClient().getAddress()}\tLOCAL")
        ClientManager.getLocalODClient().asyncDetectObjects(job) {R -> jobCompleted(job.getId(), R)}
        executingJobs[job.getId()] = job
    }
}