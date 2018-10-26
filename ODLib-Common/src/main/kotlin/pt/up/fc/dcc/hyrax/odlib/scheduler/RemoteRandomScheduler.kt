package pt.up.fc.dcc.hyrax.odlib.scheduler

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.services.ODComputingService
import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.util.*

@Suppress("unused")
class RemoteRandomScheduler : Scheduler() {
    override fun destroy() {
        jobBookkeeping.clear()
    }

    private var nextRemote = 0
    private val jobBookkeeping = HashMap<Long, Long>()

    init {
        ODLogger.logInfo("RemoteRandomScheduler starting")
    }

    private fun getNextRemoteRandom() : RemoteODClient? {
        val clients = Collections.list(ClientManager.getRemoteODClients())
        if (clients.isEmpty()) return null
        return clients[Random().nextInt(clients.size)] as RemoteODClient
    }

    override fun jobCompleted(id: Long, results: List<ODDetection?>) {
        super.jobCompleted(id, results)
        jobBookkeeping.remove(id)
    }

    override fun scheduleJob(job: ODJob) {
        if (ODComputingService.getJobsRunningCount() >= ODComputingService.getWorkingThreads()
                && ODComputingService.getPendingJobsCount() >= ODComputingService.getWorkingThreads()) {
            val nextClient = getNextRemoteRandom()
            if (nextClient != null) {
                jobBookkeeping[job.getId()] = nextClient.getId()
                nextClient.asyncDetectObjects(job) {R -> jobCompleted(job.getId(), R)}
                return
            }
        }
        ClientManager.getLocalODClient().asyncDetectObjects(job) { R -> jobCompleted(job.getId(), R)}
    }
}