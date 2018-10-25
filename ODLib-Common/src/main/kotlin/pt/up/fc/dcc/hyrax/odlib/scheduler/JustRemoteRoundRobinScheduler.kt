package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.interfaces.JobResultCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.jobManager.JobManager
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.util.*
import kotlin.collections.HashMap


@Suppress("unused")
class JustRemoteRoundRobinScheduler : Scheduler() {
    override fun destroy() {
        jobBookkeeping.clear()
    }

    private var nextRemote = 0
    private val jobBookkeeping = HashMap<Long, Long>()

    init {
        ODLogger.logInfo("JustRemoteRoundRobinScheduler starting")
    }

    private fun getNextRemoteRoundRobin() : RemoteODClient? {
        val clients = Collections.list(ClientManager.getRemoteODClients())
        if (clients.isEmpty()) return null
        nextRemote %= clients.size
        return clients[nextRemote++] as RemoteODClient
    }

    override fun jobCompleted(id: Long, results: List<ODUtils.ODDetection?>) {
        super.jobCompleted(id, results)
        jobBookkeeping.remove(id)
    }

    override fun scheduleJob(job: ODJob) {
            val nextClient = getNextRemoteRoundRobin()
            if (nextClient != null) {
                jobBookkeeping[job.getId()] = nextClient.getId()
                //val start = System.currentTimeMillis()
                nextClient.asyncDetectObjects(job) {R -> jobCompleted(job.getId(), R)}
                //nextClient.getLatencyMovingAverage().addLatency(System.currentTimeMillis()-start)
                //ODLogger.logInfo(nextClient.getLatencyMovingAverage().getAvgLatency().toString())
        }
    }
}