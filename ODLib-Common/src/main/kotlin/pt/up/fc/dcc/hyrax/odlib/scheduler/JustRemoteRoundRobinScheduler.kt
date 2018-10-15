package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.interfaces.JobResultCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.util.*
import kotlin.collections.HashMap


@Suppress("unused")
class JustRemoteRoundRobinScheduler : Scheduler {
    override var jobResultCallback: JobResultCallback? = null
    private var nextRemote = 0
    private val jobBookkeeping = HashMap<Long, Long>()

    init {
        ODLogger.logInfo("JustRemoteRoundRobinScheduler starting")
    }

    constructor()

    constructor(jobResultCallback: JobResultCallback) {
        this.jobResultCallback = jobResultCallback
    }

    private fun getNextRemoteRoundRobin() : RemoteODClient? {
        val clients = Collections.list(ClientManager.getRemoteODClients())
        if (clients.isEmpty()) return null
        return clients[nextRemote++ % clients.size] as RemoteODClient
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
            val nextClient = getNextRemoteRoundRobin()
            if (nextClient != null) {
                jobBookkeeping[job.getId()] = nextClient.id
                nextClient.asyncDetectObjects(job) {R -> jobCompleted(job.getId(), R)}
        }
    }
}