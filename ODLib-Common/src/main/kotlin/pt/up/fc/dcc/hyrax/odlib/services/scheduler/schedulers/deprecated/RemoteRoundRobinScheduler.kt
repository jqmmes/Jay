package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers.deprecated


import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger


@Suppress("unused")
class RemoteRoundRobinScheduler : SchedulerBase("RemoteRoundRobin") {
    override fun destroy() {
        jobBookkeeping.clear()
    }

    private var nextRemote: Int = 0
    private val jobBookkeeping = HashMap<String, String>()

    init {
        ODLogger.logInfo("RemoteRoundRobinScheduler starting")
    }

    private fun getNextRemoteRoundRobin() : ODProto.Worker? {
        val clients = SchedulerService.getWorkers()
        if (clients.isEmpty()) return null
        nextRemote %= clients.size
        return clients[clients.keys.toList()[nextRemote++]]
    }

    override fun jobCompleted(id: String, results: List<ODDetection?>) {
        super.jobCompleted(id, results)
        jobBookkeeping.remove(id)
    }

    override fun scheduleJob(job: ODJob) {
            val nextClient = getNextRemoteRoundRobin()
            if (nextClient != null) {
                //ODLogger.logInfo("Job_Scheduled\t${job.id}\t${nextClient.getAddress()}\tROUND_ROBIN")
                jobBookkeeping[job.id] = nextClient.id
                //nextClient.asyncDetectObjects(job) {R -> jobCompleted(job.id, R)}
                return
            }
        }
}