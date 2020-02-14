package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Job
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.LinkedBlockingDeque
import kotlin.random.Random

@Suppress("DuplicatedCode")
class ComputationEstimateScheduler : AbstractScheduler("ComputationEstimateScheduler") {
    private var rankedWorkers = LinkedBlockingDeque<RankedWorker>()

    override fun init() {
        JayLogger.logInfo("INIT")
        SchedulerService.registerNotifyListener { W, S -> if (S == SchedulerService.WorkerConnectivityStatus.ONLINE) updateWorker(W) else removeWorker(W) }
        rankWorkers(SchedulerService.getWorkers().values.toList())
        SchedulerService.listenForWorkers(true) {
            JayLogger.logInfo("LISTEN_FOR_WORKERS", actions = *arrayOf("SCHEDULER_ID=$id"))
            SchedulerService.enableHeartBeat(getWorkerTypes()) {
                JayLogger.logInfo("COMPLETE")
                super.init()
            }
        }
    }

    private fun removeWorker(worker: JayProto.Worker?) {
        JayLogger.logInfo("INIT", actions = *arrayOf("WORKER_ID=${worker?.id}"))
        val index = rankedWorkers.indexOf(RankedWorker(id = worker?.id))
        if (index == -1) return
        rankedWorkers.remove(rankedWorkers.elementAt(index))
        JayLogger.logInfo("COMPLETE", actions = *arrayOf("WORKER_ID=${worker?.id}"))
    }

    // Return last ID higher estimatedDuration = Better worker
    override fun scheduleJob(job: Job): JayProto.Worker? {
        JayLogger.logInfo("INIT", job.id)
        JayLogger.logInfo("START_SORTING", job.id)
        rankedWorkers = LinkedBlockingDeque(rankedWorkers.sortedWith(compareBy { it.weightQueue }))
        JayLogger.logInfo("COMPLETE_SORTING", job.id)
        JayLogger.logInfo("SELECTED_WORKER", job.id, actions = *arrayOf("WORKER_ID=${rankedWorkers.first.id}"))
        if (rankedWorkers.isNotEmpty()) return SchedulerService.getWorker(rankedWorkers.first.id!!)
        return null
    }

    override fun destroy() {
        SchedulerService.disableBandwidthEstimates()
        SchedulerService.listenForWorkers(false)
        rankedWorkers.clear()
        super.destroy()
    }

    override fun getWorkerTypes(): JayProto.WorkerTypes {
        return JayUtils.genWorkerTypes(JayProto.Worker.Type.LOCAL, JayProto.Worker.Type.CLOUD, JayProto.Worker.Type.REMOTE)
    }

    private fun rankWorkers(workers: List<JayProto.Worker?>) {
        for (worker in workers) updateWorker(worker)
    }

    private fun updateWorker(worker: JayProto.Worker?) {
        if (worker?.type == JayProto.Worker.Type.REMOTE && JaySettings.CLOUDLET_ID != "" && JaySettings.CLOUDLET_ID != worker.id)
            return
        if (RankedWorker(id = worker?.id) !in rankedWorkers) {
            rankedWorkers.addLast(RankedWorker(Random.nextFloat(), worker!!.id))
        }
        rankedWorkers.elementAt(rankedWorkers.indexOf(RankedWorker(id = worker?.id))).updateWorker(worker)
    }

    companion object {
        private var maxAvgTimePerJob = 0L
    }


    private data class RankedWorker(var estimatedDuration: Float = 0.0f, val id : String?) {

        override fun hashCode(): Int {
            return id.hashCode()
        }

        var weightQueue = 0L

        fun updateWorker(worker: JayProto.Worker?) {
            JayLogger.logInfo("INIT", actions = *arrayOf("WORKER_ID=$id"))
            if (worker == null) return
            if (maxAvgTimePerJob < worker.avgTimePerJob) maxAvgTimePerJob = worker.avgTimePerJob

            weightQueue = (worker.queuedJobs + 1) * worker.avgTimePerJob
            JayLogger.logInfo("WEIGHT_UPDATED", actions = *arrayOf("WORKER_ID=$id", "QUEUE_SIZE=${worker.queuedJobs}+1", "AVG_TIME_PER_JOB=${worker.avgTimePerJob}", "WEIGHT_QUEUE=$weightQueue"))
            JayLogger.logInfo("COMPLETE", actions = *arrayOf("WORKER_ID=$id"))
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