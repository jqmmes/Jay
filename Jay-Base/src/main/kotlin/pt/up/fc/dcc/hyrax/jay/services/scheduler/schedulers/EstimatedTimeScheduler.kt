/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.*
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import java.util.concurrent.LinkedBlockingDeque
import kotlin.random.Random

/**
 * TODO: Update avg computing and avg bandwidth when device is removed... or use one of the avg from the last x updates
 */
@Suppress("DuplicatedCode")
class EstimatedTimeScheduler : AbstractScheduler("EstimatedTimeScheduler") {
    private var rankedWorkers = LinkedBlockingDeque<RankedWorker>()
    private val assignedTask = LinkedHashMap<String, String>()
    private val offloadedTasks = LinkedHashMap<String, Pair<Long, Long?>>()
    private val offloadedLock = Object()
    override var description: String? = "Estimated Time Scheduler tries to minimize task completion time"

    override fun init() {
        JayLogger.logInfo("INIT")
        SchedulerService.registerNotifyListener { W, S -> if (S == SchedulerService.WorkerConnectivityStatus.ONLINE) updateWorker(W) else removeWorker(W) }
        rankWorkers(SchedulerService.getWorkers().values.toList())
        SchedulerService.listenForWorkers(true) {
            JayLogger.logInfo("LISTEN_FOR_WORKERS", actions = arrayOf("SCHEDULER_ID=$id"))
            SchedulerService.enableBandwidthEstimates(
                BandwidthEstimationConfig(BandwidthEstimationType.ACTIVE, getWorkerTypes())
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

    private fun removeWorker(worker: WorkerInfo?) {
        JayLogger.logInfo("INIT", actions = arrayOf("WORKER_ID=${worker?.id}"))
        val index = rankedWorkers.indexOf(RankedWorker(id = worker?.id))
        if (index == -1) return
        rankedWorkers.remove(rankedWorkers.elementAt(index))
        JayLogger.logInfo("COMPLETE", actions = arrayOf("WORKER_ID=${worker?.id}"))
    }

    // Return last ID higher estimatedDuration = Better worker
    override fun scheduleTask(taskInfo: TaskInfo): WorkerInfo? {
        JayLogger.logInfo("INIT", taskInfo.getId())
        for (worker in rankedWorkers) worker.calcScore(taskInfo.dataSize)
        JayLogger.logInfo("START_SORTING", taskInfo.getId())
        rankedWorkers = LinkedBlockingDeque(rankedWorkers.sortedWith(compareBy { it.estimatedDuration }))
        JayLogger.logInfo("COMPLETE_SORTING", taskInfo.getId())
        JayLogger.logInfo("SELECTED_WORKER", taskInfo.getId(), actions = arrayOf("WORKER_ID=${rankedWorkers.first.id}"))
        if (rankedWorkers.isNotEmpty()) {
            if (taskInfo.deadline != null) {
                val workerInfoMap = SchedulerService.getWorkers()
                rankedWorkers.forEach { rankedWorker ->
                    if (workerInfoMap.containsKey(rankedWorker.id)) {
                        if (SchedulerService.canMeetDeadline(taskInfo, workerInfoMap[rankedWorker.id])) {
                            synchronized(offloadedLock) {
                                assignedTask[taskInfo.getId()] = rankedWorker.id!!
                                offloadedTasks[taskInfo.getId()] = Pair(taskInfo.creationTimeStamp, taskInfo.deadline)
                            }
                            return workerInfoMap[rankedWorker.id]
                        }
                    }
                }
                JayLogger.logWarn("CANNOT_MEET_DEADLINE", taskInfo.getId())
            }
            offloadedTasks[taskInfo.getId()] = Pair(taskInfo.creationTimeStamp, taskInfo.deadline)
            assignedTask[taskInfo.getId()] = rankedWorkers.first.id!!
            return SchedulerService.getWorker(rankedWorkers.first.id!!)
        }
        return null
    }


    override fun destroy() {
        SchedulerService.disableBandwidthEstimates()
        SchedulerService.listenForWorkers(false)
        rankedWorkers.clear()
        super.destroy()
    }

    override fun getWorkerTypes(): Set<WorkerType> {
        return setOf(WorkerType.LOCAL, WorkerType.CLOUD, WorkerType.REMOTE)
    }

    private fun rankWorkers(workers: List<WorkerInfo?>) {
        for (worker in workers) updateWorker(worker)
    }

    private fun updateWorker(worker: WorkerInfo?) {
        if (worker?.type == WorkerType.REMOTE && JaySettings.CLOUDLET_ID != "" && JaySettings.CLOUDLET_ID != worker.id)
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

        fun updateWorker(worker: WorkerInfo?) {
            JayLogger.logInfo("INIT", actions = arrayOf("WORKER_ID=$id"))
            if (worker == null) return
            if (maxAvgTimePerTask < worker.getAvgComputingTimeEstimate()) maxAvgTimePerTask = worker.getAvgComputingTimeEstimate()
            if (maxBandwidthEstimate < worker.bandwidthEstimate) maxBandwidthEstimate = worker.bandwidthEstimate.toLong()
            weightQueue = (worker.queuedTasks + worker.getWaitingToReceiveTasks() + 1) * worker.getAvgComputingTimeEstimate()
            estimatedBandwidth = worker.bandwidthEstimate
            JayLogger.logInfo("WEIGHT_UPDATED", actions = arrayOf("WORKER_ID=$id", "QUEUE_SIZE=${
                worker
                        .queuedTasks
            }+1", "AVG_TIME_PER_TASK=${worker.getAvgComputingTimeEstimate()}", "WEIGHT_QUEUE=$weightQueue", "BANDWIDTH=$estimatedBandwidth"))
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