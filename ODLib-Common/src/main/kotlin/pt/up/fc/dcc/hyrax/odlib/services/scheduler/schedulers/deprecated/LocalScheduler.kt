package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.deprecated

import pt.up.fc.dcc.hyrax.odlib.utils.ODJob

class LocalScheduler : SchedulerBase("LocalScheduler") {
    override fun destroy() {
        executingJobs.clear()
    }

    private val executingJobs = HashMap<String, ODJob>()

    override fun scheduleJob(job: ODJob) {
        /*ODLogger.logInfo("Job_Scheduled\t${job.id}\t${ClientManager.getLocalODClient().getAddress()}\tLOCAL")
        ClientManager.getLocalODClient().asyncDetectObjects(job) {R -> jobCompleted(job.id, R)}
        executingJobs[job.id] = job*/
    }
}