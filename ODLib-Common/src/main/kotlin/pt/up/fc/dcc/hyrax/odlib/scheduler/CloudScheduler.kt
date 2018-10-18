package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger

class CloudScheduler : Scheduler() {
    override fun destroy() {

    }

    init {
        ODLogger.logInfo("Starting LocalScheduler...")
    }

    override fun scheduleJob(job: ODJob) {
        ClientManager.getCloudClient().asyncDetectObjects(job) {R -> jobCompleted(job.getId(), R)}
    }
}