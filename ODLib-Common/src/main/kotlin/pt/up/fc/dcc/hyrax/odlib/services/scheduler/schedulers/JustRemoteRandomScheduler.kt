package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.util.*

@Suppress("unused")
class JustRemoteRandomScheduler : Scheduler() {
    override fun destroy() {
        jobBookkeeping.clear()
    }

    private var nextRemote = 0
    private val jobBookkeeping: HashMap<String, Long> = HashMap()

    init {
        ODLogger.logInfo("JustRemoteRandomScheduler starting")
    }
    private fun getNextRemoteRandom() : RemoteODClient? {
        val clients = ClientManager.getRemoteODClients(false)
        if (clients.isEmpty()) return null
        return clients[Random().nextInt(clients.size)]
    }

    override fun jobCompleted(id: String, results: List<ODDetection?>) {
        super.jobCompleted(id, results)
        jobBookkeeping.remove(id)
    }

    override fun scheduleJob(job: ODJob) {
            val nextClient = getNextRemoteRandom()
            if (nextClient != null) {
                ODLogger.logInfo("Job_Scheduled\t${job.id}\t${nextClient.getAddress()}\tJUST_REMOTE_RANDOM")
                jobBookkeeping[job.id] = nextClient.getId()
                nextClient.asyncDetectObjects(job) {R -> jobCompleted(job.id, R)}
        }
    }
}