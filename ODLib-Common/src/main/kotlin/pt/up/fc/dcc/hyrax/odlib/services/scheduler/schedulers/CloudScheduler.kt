package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers


import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger

class CloudScheduler : Scheduler() {
    override fun destroy() {

    }

    init {
        ODLogger.logInfo("Starting CloudScheduler...")
    }

    override fun scheduleJob(job: ODJob) {
        /*ODLogger.logInfo("Job_Scheduled\t${job.id}\t${ClientManager.getCloudClient().getAddress()}\tCLOUD")
        ClientManager.getCloudClient().asyncDetectObjects(job) {R -> jobCompleted(job.id, R)}*/
    }
}