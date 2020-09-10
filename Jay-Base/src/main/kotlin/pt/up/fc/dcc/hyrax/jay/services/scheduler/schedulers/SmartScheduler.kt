package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.LinkedBlockingDeque
import kotlin.random.Random

class SmartScheduler : AbstractScheduler("SmartScheduler") {
    private var rankedWorkers = LinkedBlockingDeque<RankedWorker>()
    private var maxAvgTimePerTask = 0L
    private var maxBandwidthEstimate = 0L
    private var weights: JayProto.Weights = JayProto.Weights.newBuilder().setComputeTime(0.5f).setQueueSize(0.1f)
            .setRunningTasks(0.1f).setBattery(0.2f).setBandwidth(0.1f).build()

    override fun init() {
        JayLogger.logInfo("INIT")
        SchedulerService.registerNotifyListener { W, S -> if (S == SchedulerService.WorkerConnectivityStatus.ONLINE) rankWorker(W) else removeWorker(W) }
        rankWorkers(SchedulerService.getWorkers().values.toList())
        SchedulerService.listenForWorkers(true) {
            SchedulerService.broker.enableBandwidthEstimates(
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
    override fun scheduleTask(task: Task): JayProto.Worker? {
        JayLogger.logInfo("INIT", task.id)
        if (rankedWorkers.isNotEmpty()) return SchedulerService.getWorker(rankedWorkers.last.id!!)
        JayLogger.logInfo("COMPLETE", task.id, actions = arrayOf("WORKER_ID=${rankedWorkers.last.id}"))
        return null
    }

    override fun destroy() {
        JayLogger.logInfo("INIT")
        SchedulerService.broker.disableBandwidthEstimates()
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
        if (maxAvgTimePerTask < worker.avgTimePerTask) maxAvgTimePerTask = worker.avgTimePerTask
        if (maxBandwidthEstimate < worker.bandwidthEstimate) maxBandwidthEstimate = worker.bandwidthEstimate.toLong()

        /*
        * Lower the better >= 0
        */
        // Assuming 100ms as top latency, reserve score
        //val latency = 1f-crossMultiplication(worker..getAvgLatency().toFloat(), 100f)
        // Assuming a total of 50 tasks as max, reverse score
        //val totalTasks = 1f-crossMultiplication(remoteClients[clientID]!!.pendingTasks.toFloat(), 5f)
        val runningTasks = 1f - crossMultiplication(worker.runningTasks.toFloat(), worker.cpuCores.toFloat())

        //val available spots
        val queueSpace = crossMultiplication(worker.queueSize.toFloat() - worker.queuedTasks.toFloat(), Integer.MAX_VALUE.toFloat())

        /*
        * Higher the better >= 0
        */
        // assuming 100% battery
        val scaledBattery = crossMultiplication(worker.batteryLevel.toFloat(), 100f)
        // Relative value --- Lower is better
        val scaledAvgTimePerTask = 1f - crossMultiplication(worker.avgTimePerTask.toFloat(), maxAvgTimePerTask.toFloat())

        val scaledAvgBandwidth = 1f - crossMultiplication(worker.bandwidthEstimate, maxBandwidthEstimate.toFloat())

        val score =
                scaledAvgTimePerTask * weights.computeTime +
                        runningTasks * weights.runningTasks +
                        queueSpace * weights.queueSize +
                        scaledBattery * weights.battery +
                        scaledAvgBandwidth * weights.bandwidth

        JayLogger.logInfo("NEW_SCORE", actions = arrayOf("WORKER_ID=$${worker.id}", "SCORE=$score",
                "RUNNING_TASKS=${worker.runningTasks}", "QUEUE_SIZE=${worker.queueSize}", "BATTERY=${
            worker
                    .batteryLevel
        }", "AVG_TIME_PER_TASK=${worker.avgTimePerTask}", "BANDWIDTH_ESTIMATE=${
            worker
                    .bandwidthEstimate
        }"))
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