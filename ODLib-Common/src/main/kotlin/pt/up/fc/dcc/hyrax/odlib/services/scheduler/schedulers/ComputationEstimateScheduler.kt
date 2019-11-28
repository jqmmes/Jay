package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.structures.Job
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.LinkedBlockingDeque
import kotlin.random.Random

@Suppress("DuplicatedCode")
class ComputationEstimateScheduler : Scheduler("ComputationEstimateScheduler") {
    private var rankedWorkers = LinkedBlockingDeque<RankedWorker>()

    override fun init() {
        ODLogger.logInfo("INIT")
        SchedulerService.registerNotifyListener { W, S ->  if (S == SchedulerService.WorkerConnectivityStatus.ONLINE) updateWorker(W) else removeWorker(W) }
        rankWorkers(SchedulerService.getWorkers().values.toList())
        SchedulerService.listenForWorkers(true) {
            ODLogger.logInfo("LISTEN_FOR_WORKERS", actions = *arrayOf("SCHEDULER_ID=$id"))
            SchedulerService.enableHeartBeat(getWorkerTypes()){
                ODLogger.logInfo("COMPLETE")
                super.init()
            }
        }
    }

    private fun removeWorker(worker: ODProto.Worker?) {
        ODLogger.logInfo("INIT", actions = *arrayOf("WORKER_ID=${worker?.id}"))
        val index = rankedWorkers.indexOf(RankedWorker(id=worker?.id))
        if (index == -1) return
        rankedWorkers.remove(rankedWorkers.elementAt(index))
        ODLogger.logInfo("COMPLETE", actions = *arrayOf("WORKER_ID=${worker?.id}"))
    }

    // Return last ID higher estimatedDuration = Better worker
    override fun scheduleJob(job: Job): ODProto.Worker? {
        ODLogger.logInfo("INIT",job.id)
        ODLogger.logInfo("START_SORTING", job.id)
        rankedWorkers = LinkedBlockingDeque(rankedWorkers.sortedWith(compareBy {it.weightQueue}))
        ODLogger.logInfo("COMPLETE_SORTING", job.id)
        ODLogger.logInfo("SELECTED_WORKER", job.id, actions = *arrayOf("WORKER_ID=${rankedWorkers.first.id}"))
        if (rankedWorkers.isNotEmpty()) return SchedulerService.getWorker(rankedWorkers.first.id!!)
        return null
    }

    override fun destroy() {
        SchedulerService.disableBandwidthEstimates()
        SchedulerService.listenForWorkers(false)
        rankedWorkers.clear()
        super.destroy()
    }

    override fun getWorkerTypes(): ODProto.WorkerTypes {
        return ODUtils.genWorkerTypes(ODProto.Worker.Type.LOCAL, ODProto.Worker.Type.CLOUD, ODProto.Worker.Type.REMOTE)
    }

    private fun rankWorkers(workers: List<ODProto.Worker?>) {
        for (worker in workers) updateWorker(worker)
    }

    private fun updateWorker(worker: ODProto.Worker?) {
        if (worker?.type == ODProto.Worker.Type.REMOTE && ODSettings.CLOUDLET_ID != "" && ODSettings.CLOUDLET_ID != worker.id)
            return
        if (RankedWorker(id=worker?.id) !in rankedWorkers) {
            rankedWorkers.addLast(RankedWorker(Random.nextFloat(), worker!!.id))
        }
        rankedWorkers.elementAt(rankedWorkers.indexOf(RankedWorker(id=worker?.id))).updateWorker(worker)
    }

    companion object {
        private var maxAvgTimePerJob = 0L
    }


    private data class RankedWorker(var estimatedDuration: Float = 0.0f, val id : String?) {

        override fun hashCode(): Int {
            return id.hashCode()
        }

        var weightQueue = 0L

        fun updateWorker(worker: ODProto.Worker?) {
            ODLogger.logInfo("INIT", actions = *arrayOf("WORKER_ID=$id"))
            if (worker == null) return
            if (maxAvgTimePerJob < worker.avgTimePerJob) maxAvgTimePerJob = worker.avgTimePerJob

            weightQueue = (worker.queuedJobs+1)*worker.avgTimePerJob
            ODLogger.logInfo("WEIGHT_UPDATED", actions = *arrayOf("WORKER_ID=$id", "QUEUE_SIZE=${worker.queuedJobs}+1", "AVG_TIME_PER_JOB=${worker.avgTimePerJob}", "WEIGHT_QUEUE=$weightQueue"))
            ODLogger.logInfo("COMPLETE", actions = *arrayOf("WORKER_ID=$id"))
        }


        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            if (javaClass != other?.javaClass) return false

            other as RankedWorker

            if (id != other.id) return false

            return true
        }
    }

}