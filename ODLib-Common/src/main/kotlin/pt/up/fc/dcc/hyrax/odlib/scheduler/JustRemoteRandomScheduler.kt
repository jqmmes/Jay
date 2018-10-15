package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.interfaces.JobResultCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.Scheduler
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.*

@Suppress("unused")
class JustRemoteRandomScheduler : Scheduler() {
    private var nextRemote = 0
    private val jobBookkeeping = HashMap<Long, Long>()
    init {
        ODLogger.logInfo("JustRemoteRandomScheduler starting")
    }
    private fun getNextRemoteRandom() : RemoteODClient? {
        val clients = Collections.list(ClientManager.getRemoteODClients())
        if (clients.isEmpty()) return null
        return clients[Random().nextInt(clients.size)] as RemoteODClient
    }

    override fun jobCompleted(id: Long, results: List<ODUtils.ODDetection?>) {
        super.jobCompleted(id, results)
        jobBookkeeping.remove(id)
    }

    override fun scheduleJob(job: ODJob) {
            val nextClient = getNextRemoteRandom()
            if (nextClient != null) {
                jobBookkeeping[job.getId()] = nextClient.id
                nextClient.asyncDetectObjects(job) {R -> jobCompleted(job.getId(), R)}
        }
    }
}