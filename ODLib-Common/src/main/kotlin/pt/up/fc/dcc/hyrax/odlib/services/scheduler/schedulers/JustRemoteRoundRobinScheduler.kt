package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger


@Suppress("unused")
class JustRemoteRoundRobinScheduler : Scheduler() {
    override fun destroy() {
        jobBookkeeping.clear()
    }

    private var nextRemote = 0
    private val jobBookkeeping = HashMap<String, Long>()

    init {
        ODLogger.logInfo("JustRemoteRoundRobinScheduler starting")
    }

    private fun getNextRemoteRoundRobin(): RemoteODClient? {
        val clients = ClientManager.getRemoteODClients(false)
        if (clients.isEmpty()) return null
        nextRemote %= clients.size
        return clients[nextRemote++]
    }

    override fun jobCompleted(id: String, results: List<ODDetection?>) {
        super.jobCompleted(id, results)
        jobBookkeeping.remove(id)
    }

    override fun scheduleJob(job: ODJob) {
        val nextClient = getNextRemoteRoundRobin()
        if (nextClient != null) {
            ODLogger.logInfo("Job_Scheduled\t${job.id}\t${nextClient.getAddress()}\tJUST_REMOTE_ROUND_ROBIN")
            jobBookkeeping[job.id] = nextClient.getId()
            //val start = System.currentTimeMillis()
            nextClient.asyncDetectObjects(job) { R -> jobCompleted(job.id, R) }
            //nextClient.getLatencyMovingAverage().addRTT(System.currentTimeMillis()-start)
            //ODLogger.logInfo(nextClient.getLatencyMovingAverage().getAvgRTT().toString())
        }
    }
}