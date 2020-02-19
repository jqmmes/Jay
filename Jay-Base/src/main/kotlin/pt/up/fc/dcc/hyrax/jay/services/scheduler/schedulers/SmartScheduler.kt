package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Job
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.LinkedBlockingDeque
import kotlin.random.Random

/**
 * TOOD: Actualizam o avg computing e avg bandwidth quando se remove um device... ou então utilizar um avg dos
 * ultimos x updates
 */
class SmartScheduler : AbstractScheduler("SmartScheduler") {
    private var rankedWorkers = LinkedBlockingDeque<RankedWorker>()
    private var maxAvgTimePerJob = 0L
    private var maxBandwidthEstimate = 0L

    override fun init() {
        JayLogger.logInfo("INIT")
        SchedulerService.registerNotifyListener { W, S -> if (S == SchedulerService.WorkerConnectivityStatus.ONLINE) rankWorker(W) else removeWorker(W) }
        rankWorkers(SchedulerService.getWorkers().values.toList())
        SchedulerService.listenForWorkers(true) {
            SchedulerService.enableBandwidthEstimates(
                    JayProto.BandwidthEstimate.newBuilder()
                            .setType(JayProto.BandwidthEstimate.Type.ACTIVE)
                            .addAllWorkerType(getWorkerTypes().typeList)
                            .build()
            ) {
                JayLogger.logInfo("COMPLETE")
                super.init()
            }
        }
        //SchedulerService.enableHeartBeat(ODUtils.genWorkerTypes()){super.init()}
    }

    private fun removeWorker(worker: JayProto.Worker?) {
        val index = rankedWorkers.indexOf(RankedWorker(id = worker?.id))
        if (index == -1) return
        rankedWorkers.remove(rankedWorkers.elementAt(index))
    }

    // Return last ID higher score = Better worker
    override fun scheduleJob(job: Job): JayProto.Worker? {
        JayLogger.logInfo("INIT", job.id)
        if (rankedWorkers.isNotEmpty()) return SchedulerService.getWorker(rankedWorkers.last.id!!)
        JayLogger.logInfo("COMPLETE", job.id, actions = *arrayOf("WORKER_ID=${rankedWorkers.last.id}"))
        return null
    }

    override fun destroy() {
        JayLogger.logInfo("INIT")
        SchedulerService.disableBandwidthEstimates()
        SchedulerService.listenForWorkers(false)
        rankedWorkers.clear()
        JayLogger.logInfo("COMPLETE")
        super.destroy()
    }

    override fun getWorkerTypes(): JayProto.WorkerTypes {
        return JayUtils.genWorkerTypes(JayProto.Worker.Type.LOCAL, JayProto.Worker.Type.CLOUD, JayProto.Worker.Type.REMOTE)
    }

    private fun rankWorkers(workers: List<JayProto.Worker?>) {
        for (worker in workers) rankWorker(worker)
    }

    private fun rankWorker(worker: JayProto.Worker?) {
        if (RankedWorker(id = worker?.id) !in rankedWorkers) {
            rankedWorkers.addLast(RankedWorker(Random.nextFloat(), worker!!.id))
        } else {
            rankedWorkers.elementAt(rankedWorkers.indexOf(RankedWorker(id = worker?.id))).score = calcScore(worker)
        }
        rankedWorkers = LinkedBlockingDeque(rankedWorkers.sortedWith(compareBy { it.score }))
    }

    private fun calcScore(worker: JayProto.Worker?): Float {
        if (worker == null) return 0.0f
        if (maxAvgTimePerJob < worker.avgTimePerJob) maxAvgTimePerJob = worker.avgTimePerJob
        if (maxBandwidthEstimate < worker.bandwidthEstimate) maxBandwidthEstimate = worker.bandwidthEstimate.toLong()

        /*
        * Lower the better >= 0
        */
        // Assuming 100ms as top latency, reserve score
        //val latency = 1f-crossMultiplication(worker..getAvgLatency().toFloat(), 100f)
        // Assuming a total of 50 jobs as max, reverse score
        //val totalJobs = 1f-crossMultiplication(remoteClients[clientID]!!.pendingJobs.toFloat(), 5f)
        val runningJobs = 1f-crossMultiplication(worker.runningJobs.toFloat(), worker.cpuCores.toFloat())

        //val available spots
        val queueSpace = crossMultiplication(worker.queueSize.toFloat() - worker.queuedJobs.toFloat(), Integer.MAX_VALUE.toFloat())

        /*
        * Higher the better >= 0
        */
        // assuming 100% battery
        val scaledBattery = crossMultiplication(worker.batteryLevel.toFloat(), 100f)
        // Relative value --- Lower is better
        val scaledAvgTimePerJob = 1f-crossMultiplication(worker.avgTimePerJob.toFloat(), maxAvgTimePerJob.toFloat())

        val scaledAvgBandwidth = 1f - crossMultiplication(worker.bandwidthEstimate, maxBandwidthEstimate.toFloat())

        val score =
                scaledAvgTimePerJob * SchedulerService.weights.computeTime +
                        runningJobs * SchedulerService.weights.runningJobs +
                        queueSpace * SchedulerService.weights.queueSize +
                        scaledBattery * SchedulerService.weights.battery +
                        scaledAvgBandwidth * SchedulerService.weights.bandwidth

        JayLogger.logInfo("NEW_SCORE", actions = *arrayOf("WORKER_ID=$${worker.id}", "SCORE=$score", "RUNNING_JOBS=${worker.runningJobs}", "QUEUE_SIZE=${worker.queueSize}", "BATTERY=${worker.batteryLevel}", "AVG_TIME_PER_JOB=${worker.avgTimePerJob}", "BANDWIDTH_ESTIMATE=${worker.bandwidthEstimate}"))
        return score
    }

    /*
    *
    * A -- B
    * x -- C(1.0)
    *
    * Converter x em 0.0-1.0
    */
    private fun crossMultiplication(A: Float, B: Float, C: Float = 1.0f): Float {
        if (B == 0.0f) return 0f
        return (C*A)/B
    }

    private data class RankedWorker(var score: Float = 0.0f, val id : String?) {

        override fun hashCode(): Int {
            return id.hashCode()
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