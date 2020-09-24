package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.LinkedBlockingDeque
import kotlin.random.Random

/**
 * TOOD: Actualizam o avg computing e avg bandwidth quando se remove um device... ou então utilizar um avg dos
 * ultimos x updates
 */
@Suppress("DuplicatedCode")
class EstimatedTimeScheduler : AbstractScheduler("EstimatedTimeScheduler") {
    private var rankedWorkers = LinkedBlockingDeque<RankedWorker>()
    private val assignedTask = LinkedHashMap<String, String>()
    private val offloadedTasks = LinkedHashMap<String, Pair<Long, Long?>>()
    private val offloadedLock = Object()

    override fun init() {
        JayLogger.logInfo("INIT")
        SchedulerService.registerNotifyListener { W, S -> if (S == SchedulerService.WorkerConnectivityStatus.ONLINE) updateWorker(W) else removeWorker(W) }
        rankWorkers(SchedulerService.getWorkers().values.toList())
        SchedulerService.listenForWorkers(true) {
            JayLogger.logInfo("LISTEN_FOR_WORKERS", actions = arrayOf("SCHEDULER_ID=$id"))
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
        SchedulerService.registerNotifyTaskListener { taskId ->
            if (taskId == "" || (taskId !in assignedTask.keys)) return@registerNotifyTaskListener
            synchronized(offloadedLock) {
                assignedTask.remove(taskId)
                JayLogger.logInfo("TASK_COMPLETE_LISTENER", taskId)
                JayLogger.logInfo("DEADLINE_DATA", taskId, offloadedTasks.keys.toString())
                if (offloadedTasks.containsKey(taskId)) {
                    JayLogger.logInfo("DEADLINE_DATA_CONTAINS_KEY", taskId, "${offloadedTasks[taskId]?.first}, ${
                        offloadedTasks[taskId]?.second
                    }")
                    if (offloadedTasks[taskId]?.second != null) {
                        //val deltaTask = (System.currentTimeMillis() - offloadedTasks[taskId]!!.first) / 1000f
                        JayLogger.logInfo("TASK_WITH_DEADLINE_COMPLETED", taskId,
                                "DEADLINE_MET=${System.currentTimeMillis() <= offloadedTasks[taskId]!!.second!!}")
                    }
                    offloadedTasks.remove(taskId)
                }
            }
        }
    }

    private fun removeWorker(worker: JayProto.Worker?) {
        JayLogger.logInfo("INIT", actions = arrayOf("WORKER_ID=${worker?.id}"))
        val index = rankedWorkers.indexOf(RankedWorker(id = worker?.id))
        if (index == -1) return
        rankedWorkers.remove(rankedWorkers.elementAt(index))
        JayLogger.logInfo("COMPLETE", actions = arrayOf("WORKER_ID=${worker?.id}"))
    }

    // Return last ID higher estimatedDuration = Better worker
    override fun scheduleTask(task: Task): JayProto.Worker? {
        JayLogger.logInfo("INIT", task.id)
        for (worker in rankedWorkers) worker.calcScore(task.dataSize)
        JayLogger.logInfo("START_SORTING", task.id)
        rankedWorkers = LinkedBlockingDeque(rankedWorkers.sortedWith(compareBy { it.estimatedDuration }))
        JayLogger.logInfo("COMPLETE_SORTING", task.id)
        JayLogger.logInfo("SELECTED_WORKER", task.id, actions = arrayOf("WORKER_ID=${rankedWorkers.first.id}"))
        if (rankedWorkers.isNotEmpty()) {
            if (task.deadline != null || task.deadlineDuration != null) {
                val workerInfoMap = SchedulerService.getWorkers()
                rankedWorkers.forEach { rankedWorker ->
                    if (workerInfoMap.containsKey(rankedWorker.id)) {
                        if (SchedulerService.canMeetDeadline(task, workerInfoMap[rankedWorker.id])) {
                            synchronized(offloadedLock) {
                                assignedTask[task.id] = rankedWorker.id!!
                                offloadedTasks[task.id] = Pair(task.creationTimeStamp, task.deadline)
                            }
                            return workerInfoMap[rankedWorker.id]
                        }
                    }
                }
                JayLogger.logWarn("CANNOT_MEET_DEADLINE", task.id)
            }
            assignedTask[task.id] = rankedWorkers.first.id!!
            return SchedulerService.getWorker(rankedWorkers.first.id!!)
        }
        return null
    }


    override fun destroy() {
        SchedulerService.broker.disableBandwidthEstimates()
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
        private var maxAvgTimePerTask = 0L
        private var maxBandwidthEstimate = 0L
    }


    private data class RankedWorker(var estimatedDuration: Float = 0.0f, val id : String?) {

        override fun hashCode(): Int {
            return id.hashCode()
        }

        private var weightQueue = 0L
        private var estimatedBandwidth = 0f

        fun updateWorker(worker: JayProto.Worker?) {
            JayLogger.logInfo("INIT", actions = arrayOf("WORKER_ID=$id"))
            if (worker == null) return
            if (maxAvgTimePerTask < worker.avgTimePerTask) maxAvgTimePerTask = worker.avgTimePerTask
            if (maxBandwidthEstimate < worker.bandwidthEstimate) maxBandwidthEstimate = worker.bandwidthEstimate.toLong()
            weightQueue = (worker.queuedTasks + worker.waitingToReceiveTasks + 1) * worker.avgTimePerTask
            estimatedBandwidth = worker.bandwidthEstimate
            JayLogger.logInfo("WEIGHT_UPDATED", actions = arrayOf("WORKER_ID=$id", "QUEUE_SIZE=${
                worker
                        .queuedTasks
            }+1", "AVG_TIME_PER_TASK=${worker.avgTimePerTask}", "WEIGHT_QUEUE=$weightQueue", "BANDWIDTH=$estimatedBandwidth"))
            JayLogger.logInfo("COMPLETE", actions = arrayOf("WORKER_ID=$id"))
        }

        fun calcScore(dataSize: Long) {
            JayLogger.logInfo("INIT", actions = arrayOf("WORKER_ID=$id"))
            estimatedDuration = dataSize * estimatedBandwidth + weightQueue
            JayLogger.logInfo("COMPLETE", actions = arrayOf("WORKER_ID=$id", "SCORE=$estimatedDuration"))
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