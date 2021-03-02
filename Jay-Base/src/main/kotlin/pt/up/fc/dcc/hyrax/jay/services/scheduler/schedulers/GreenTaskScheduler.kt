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
import kotlin.math.max
import kotlin.random.Random

/**
 * Green Task Scheduler will always chose the lowest energy spent per task not looking at queue sizes or idle times.
 * In order to control the dissemination of tasks, we need to implement deadlines
 *
 */

class GreenTaskScheduler(vararg devices: WorkerType) : AbstractScheduler("GreenTaskScheduler") {

    private var devices = devices.toList()
    private val offloadedTasks = LinkedHashMap<String, Pair<Long, Long?>>()
    private val lock = Object()
    private val offloadedLock = Object()
    override var description: String? = "Estimated Time Scheduler tries to minimize task energy consumption"

    @Suppress("DuplicatedCode")
    override fun init() {
        for (device in devices) JayLogger.logInfo("DEVICES", actions = arrayOf("DEVICE_TYPE=${device.name}"))
        if (WorkerType.REMOTE in devices) {
            SchedulerService.listenForWorkers(true) {
                SchedulerService.enableBandwidthEstimates(
                    BandwidthEstimationConfig(BandwidthEstimationType.ACTIVE,getWorkerTypes())
                ) {
                    JayLogger.logInfo("COMPLETE")
                    super.init()
                }
            }
        } else {
            SchedulerService.enableHeartBeats(getWorkerTypes()) {
                JayLogger.logInfo("COMPLETE")
                super.init()
            }
        }
        SchedulerService.registerNotifyTaskListener { taskId ->
            JayLogger.logInfo("TASK_COMPLETE_LISTENER", taskId)
            synchronized(offloadedLock) {
                JayLogger.logInfo("DEADLINE_DATA", taskId, offloadedTasks.keys.toString())
                if (offloadedTasks.containsKey(taskId)) {
                    JayLogger.logInfo("DEADLINE_DATA_CONTAINS_KEY", taskId, "${offloadedTasks[taskId]?.first}, ${
                        offloadedTasks[taskId]?.second
                    }")
                    if (offloadedTasks[taskId]?.second != null) {
                        JayLogger.logInfo("TASK_WITH_DEADLINE_COMPLETED", taskId,
                                "DEADLINE_MET=${System.currentTimeMillis() <= offloadedTasks[taskId]!!.second!!}")
                    }
                    offloadedTasks.remove(taskId)
                }
            }
        }
    }

    override fun getName(): String {
        var devices = ""
        for (devType in this.devices) devices += "$devType, "
        return "${super.getName()} [${devices.trimEnd(' ', ',')}]"
    }

    private fun greenSelection(workers: Set<WorkerInfo>, task: TaskInfo, local: WorkerInfo): Set<WorkerInfo> {
        val possibleWorkers = mutableSetOf<WorkerInfo>()
        var maxSpend = Float.NEGATIVE_INFINITY
        synchronized(lock) {
            workers.forEach { worker ->
                var spend: Float = getEnergySpentComputing(task, worker, local.getPowerEstimations()!!)
                if (JaySettings.INCLUDE_IDLE_COSTS) spend += getIdleCost(task, worker, local)
                if (spend > 0) spend = spend.unaryMinus()
                JayLogger.logInfo("EXPECTED_ENERGY_SPENT_REMOTE", task.getId(), "WORKER=${worker.id}", "SPEND=$spend")
                if (spend > maxSpend) {
                    possibleWorkers.clear()
                    maxSpend = spend
                    possibleWorkers.add(worker)
                } else if (spend == maxSpend) {
                    possibleWorkers.add(worker)
                }
            }
        }
        return possibleWorkers
    }

    private fun estimateCompletionTime(worker: WorkerInfo, task: TaskInfo): Float {
        return (worker.queuedTasks + worker.getWaitingToReceiveTasks() + 1) * worker.getAvgComputingTimeEstimate() +
                task.dataSize * worker.bandwidthEstimate +
                worker.avgResultSize * worker.bandwidthEstimate
    }

    private fun performanceSelection(workers: Set<WorkerInfo>, task: TaskInfo): Set<WorkerInfo> {
        val possibleWorkers = mutableSetOf<WorkerInfo>()
        var faster = Float.POSITIVE_INFINITY
        synchronized(lock) {
            workers.forEach { worker ->
                val estimatedTime = estimateCompletionTime(worker, task)
                JayLogger.logInfo("EXPECTED_COMPLETION_TIME", task.getId(), "WORKER=${worker.id}", "TIME=$estimatedTime")
                if (estimatedTime < faster) {
                    possibleWorkers.clear()
                    possibleWorkers.add(worker)
                    faster = estimatedTime
                } else if (estimatedTime == faster) {
                    possibleWorkers.add(worker)
                }
            }
        }
        return possibleWorkers
    }


    /**
     * In here we have to estimate how much energy will be spent on the remote device
     * i.e. task receive, queue time (remote device will be working), compute, result send
     */
    override fun scheduleTask(taskInfo: TaskInfo): WorkerInfo {
        JayLogger.logInfo("BEGIN_SCHEDULING", taskInfo.getId())
        val workers = SchedulerService.getWorkers(devices).values
        val meetDeadlines = mutableSetOf<WorkerInfo>()
        val local = SchedulerService.getWorkers(WorkerType.LOCAL).values.elementAt(0)!!

        workers.forEach { worker ->
            if (SchedulerService.canMeetDeadline(taskInfo, worker) && worker != null) {
                meetDeadlines.add(worker)
            }
        }
        val possibleWorkers = if (meetDeadlines.isEmpty()) {
            JayLogger.logWarn("CANNOT_MEET_DEADLINE", taskInfo.getId())
            workers.forEach { if (it != null) meetDeadlines.add(it) }
            when (JaySettings.TASK_DEADLINE_BROKEN_SELECTION) {
                "EXECUTE_LOCALLY" -> setOf(SchedulerService.getWorkers(WorkerType.LOCAL).values.first()!!)
                "FASTER_COMPLETION" -> performanceSelection(meetDeadlines, taskInfo)
                "RANDOM" -> meetDeadlines
                "LOWEST_ENERGY" -> greenSelection(meetDeadlines, taskInfo, local)
                else -> meetDeadlines
            }
        } else {
            greenSelection(meetDeadlines, taskInfo, local)
        }

        val w = if (possibleWorkers.isNotEmpty()) {
            possibleWorkers.elementAt(Random.nextInt(possibleWorkers.size))
        } else {
            workers.forEach { if (it != null) meetDeadlines.add(it) }
            meetDeadlines.elementAt(Random.nextInt(workers.size))
        }
        synchronized(offloadedLock) {
            offloadedTasks[taskInfo.getId()] = Pair(taskInfo.creationTimeStamp, taskInfo.deadline)
        }
        JayLogger.logInfo("COMPLETE_SCHEDULING", taskInfo.getId(), "WORKER=${w.id}")
        return w
    }


    private fun getIdleCost(task: TaskInfo, worker: WorkerInfo, local: WorkerInfo): Float {
        if (worker.id == local.id) {
            return 0.0f
        }
        val expectedTaskTime = ((worker.queuedTasks + worker.getWaitingToReceiveTasks() + 1) * worker.getAvgComputingTimeEstimate()) +
                (worker.bandwidthEstimate.toLong() * task.dataSize) +
                (worker.avgResultSize * worker.bandwidthEstimate)
        val localComputingTime = ((local.queuedTasks + local.getWaitingToReceiveTasks()) * local.getAvgComputingTimeEstimate())
        return ((max(0f, expectedTaskTime - localComputingTime) / 1000) / 3600) * local.getPowerEstimations()!!.idle
    }

    private fun getEnergySpentComputing(task: TaskInfo, worker: WorkerInfo, local: PowerEstimations): Float {
        if (worker.getPowerEstimations()!!.compute == 0.0f && worker.getPowerEstimations()!!.idle == 0.0f
                && worker.getPowerEstimations()!!.rx == 0.0f && worker.getPowerEstimations()!!.tx == 0.0f
        ) return Float.NEGATIVE_INFINITY
        JayLogger.logInfo("ENERGY_SPENT_ESTIMATION_PARAMS", task.getId(),
                "AVG_TIME_PER_TASK=${worker.getAvgComputingTimeEstimate()}",
                "BANDWIDTH_ESTIMATE=${worker.getBandwidthEstimate()}",
                "AVG_RESULT_SIZE=${worker.getAverageResultSize()}",
                "POWER_REMOTE_COMPUTE=${worker.getPowerEstimations()!!.compute}",
                "POWER_LOCAL_TX=${local.tx}",
                "POWER_REMOTE_RX=${worker.getPowerEstimations()!!.rx}",
                "POWER_REMOTE_TX=${worker.getPowerEstimations()!!.tx}"
        )
        return ((worker.getAvgComputingTimeEstimate() / 1000f) / 3600f) * worker.getPowerEstimations()!!.compute +
                if (worker.type == WorkerType.LOCAL) {
                    (((worker.bandwidthEstimate.toLong() * task.dataSize) / 1000f) / 3600) * local.tx +
                            (((worker.bandwidthEstimate.toLong() * task.dataSize) / 1000f) / 3600) * worker.getPowerEstimations()!!.rx +
                            (((worker.avgResultSize * worker.bandwidthEstimate.toLong()) / 1000f) / 3600) * worker.getPowerEstimations()!!.tx
                } else {
                    0f
                }
    }

    override fun destroy() {
        JayLogger.logInfo("INIT")
        JayLogger.logInfo("COMPLETE")
        super.destroy()
    }

    override fun getWorkerTypes(): Set<WorkerType> {
        return setOf(WorkerType.CLOUD, WorkerType.REMOTE, WorkerType.LOCAL)
    }
}