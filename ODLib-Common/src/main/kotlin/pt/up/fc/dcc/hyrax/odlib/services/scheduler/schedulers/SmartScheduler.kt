package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.structures.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.LinkedBlockingDeque
import kotlin.random.Random

/**
 * TOOD: Actualizam o avg computing e avg bandwidth quando se remove um device... ou então utilizar um avg dos
 * ultimos x updates
 */
class SmartScheduler : Scheduler("SmartScheduler") {
    private val rankedWorkers = LinkedBlockingDeque<RankedWorker>()
    private var maxAvgTimePerJob = 0L
    private var maxBandwidthEstimate = 0L

    override fun init() {
        SchedulerService.registerNotifyListener { W, S ->  if (S == SchedulerService.WorkerConnectivityStatus.ONLINE) rankWorker(W) else removeWorker(W) }
        rankWorkers(SchedulerService.getWorkers().values.toList())
        SchedulerService.enableBandwidthEstimates(
                ODProto.BandwidthEstimate.newBuilder()
                        .setType(ODProto.BandwidthEstimate.Type.ACTIVE)
                        .addAllWorkerType(getWorkerTypes().typeList)
                        .build()
        )
        super.init()
    }

    private fun removeWorker(worker: ODProto.Worker?) {
        val index = rankedWorkers.indexOf(RankedWorker(id=worker?.id))
        if (index == -1) return
        rankedWorkers.remove(rankedWorkers.elementAt(index))
    }

    // Return last ID higher score = Better worker
    override fun scheduleJob(job: ODJob): ODProto.Worker? {
        return SchedulerService.getWorker(rankedWorkers.last.id!!)
    }

    override fun destroy() {
        SchedulerService.disableBandwidthEstimates()
        rankedWorkers.clear()

    }

    override fun getWorkerTypes(): ODProto.WorkerTypes {
        return ODUtils.genWorkerTypes(ODProto.Worker.Type.LOCAL, ODProto.Worker.Type.CLOUD, ODProto.Worker.Type.REMOTE)
    }

    private fun rankWorkers(workers: List<ODProto.Worker?>) {
        for (worker in workers) rankWorker(worker)
    }

    /*private fun reSortClientList(clientID: Long) {
        *//*val client = clientList.find { T -> T.second == clientID }
        if (client != null) clientList.removeAt(clientList.indexOf(client))
        clientList.add(Pair(calculateClientScore(clientID), clientID))*//*
                clientList.sortWith(Comparator { lhs, rhs -> java.lang.Float.compare(rhs.first, lhs.first) })
    }*/

    private fun rankWorker(worker: ODProto.Worker?) {
        if (RankedWorker(id=worker?.id) !in rankedWorkers) {
            rankedWorkers.addLast(RankedWorker(Random.nextFloat(), worker!!.id))
        } else {
            rankedWorkers.elementAt(rankedWorkers.indexOf(RankedWorker(id=worker?.id))).score = calcScore(worker)
        }
        rankedWorkers.sortedWith(compareBy {it.score})
    }

    private fun calcScore(worker: ODProto.Worker?): Float {
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
        val queueSpace = crossMultiplication(worker.queueSize.toFloat(), Integer.MAX_VALUE.toFloat())

        /*
        * Higher the better >= 0
        */
        // assuming 100% battery
        val scaledBattery = crossMultiplication(worker.battery.toFloat(), 100f)
        // Relative value --- Lower is better
        val scaledAvgTimePerJob = 1f-crossMultiplication(worker.avgTimePerJob.toFloat(), maxAvgTimePerJob.toFloat())

        val scaledAvgBandwidth = 1f-crossMultiplication(worker.bandwidthEstimate.toFloat(), maxBandwidthEstimate.toFloat())

        val score =
                scaledAvgTimePerJob * SchedulerService.weights.computeTime +
                        runningJobs * SchedulerService.weights.runningJobs +
                        queueSpace * SchedulerService.weights.queueSize +
                        scaledBattery * SchedulerService.weights.battery +
                        scaledAvgBandwidth * SchedulerService.weights.bandwidth

        ODLogger.logInfo("New Score for ${worker.id}: $score")

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