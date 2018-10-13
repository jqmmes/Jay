package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.interfaces.JobResultCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob
import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.*

@Suppress("unused")
class RemoteRandomScheduler : Scheduler {
    override var jobResultCallback: JobResultCallback? = null
    private var nextRemote = 0
    private val jobBookkeeping = HashMap<Long, Long>()

    constructor()

    constructor(jobResultCallback: JobResultCallback) {
        this.jobResultCallback = jobResultCallback
    }

    private fun getNextRemoteRandom() : RemoteODClient? {
        val clients = Collections.list(ClientManager.getRemoteODClients())
        if (clients.isEmpty()) return null
        return clients[Random().nextInt(clients.size)] as RemoteODClient
    }

    override fun setJobCompleteCallback(callback: JobResultCallback) {
        jobResultCallback = callback
    }

    override fun jobCompleted(id: Long, results: List<ODUtils.ODDetection?>) {
        ODLogger.logInfo("Job $id completed\n\t\t$results")
        jobBookkeeping.remove(id)
        if (jobResultCallback != null) jobResultCallback!!.onNewResult(results)
    }

    override fun scheduleJob(job: ODJob) {
        if (ODComputingService.getJobsRunningCount() > ODComputingService.getWorkingThreads()
                && ODComputingService.getPendingJobsCount() > ODComputingService.getWorkingThreads()) {
            val nextClient = getNextRemoteRandom()
            if (nextClient != null) {
                jobBookkeeping[job.getId()] = nextClient.id
                nextClient.asyncDetectObjects(job) {R -> jobCompleted(job.getId(), R)}
            } else {
                ClientManager.getLocalODClient().asyncDetectObjects(job) { R -> jobCompleted(job.getId(), R)}
            }
        }
    }
}