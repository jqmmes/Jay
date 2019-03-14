package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.deprecated

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.util.*

@Suppress("unused")
class RemoteRandomScheduler : SchedulerBase("RemoteRandom") {
    override fun destroy() {
        jobBookkeeping.clear()
    }

    private var nextRemote = 0
    private val jobBookkeeping = HashMap<String, String>()

    init {
        ODLogger.logInfo("RemoteRandomScheduler starting")
    }

    private fun getNextRemoteRandom() : ODProto.Worker? {
        val clients = SchedulerService.getWorkers()
        if (clients.isEmpty()) return null
        return clients[clients.keys.toList()[Random().nextInt(clients.size)]]
    }

    override fun jobCompleted(id: String, results: List<ODDetection?>) {
        super.jobCompleted(id, results)
        jobBookkeeping.remove(id)
    }

    override fun scheduleJob(job: ODJob) {
            val nextClient = getNextRemoteRandom()
            if (nextClient != null) {
                //ODLogger.logInfo("Job_Scheduled\t${job.id}\t${nextClient.getAddress()}\tRANDOM")
                jobBookkeeping[job.id] = nextClient.id
                //nextClient.asyncDetectObjects(job) {R -> jobCompleted(job.id, R)}
                return
            }
        }
}