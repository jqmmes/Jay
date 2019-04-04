package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.structures.ODJob
import java.util.concurrent.LinkedBlockingDeque
import kotlin.random.Random

class SmartScheduler : Scheduler("SmartScheduler") {

    private val rankedWorkers = LinkedBlockingDeque<RankedWorker>()
    private var maxAvgTimePerJob = 0L
    private var maxBandwidthEstimate = 0L

    override fun init() {
        SchedulerService.registerNotifyListener { W ->  rankWorker(W) }
        rankWorkers(SchedulerService.getWorkers().values.toList())
        super.init()
    }

    // Return last ID higher score = Better worker
    override fun scheduleJob(job: ODJob): ODProto.Worker? {
        return SchedulerService.getWorker(rankedWorkers.last.id!!)
    }

    override fun destroy() {
        rankedWorkers.clear()

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

    private fun calcScore(worker: ODProto.Worker?) : Float{
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



        val score = scaledAvgTimePerJob*0.20f+runningJobs*0.25f+queueSpace*0.2f+scaledBattery*0.25f+scaledAvgBandwidth*0.10f
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