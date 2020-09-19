package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import kotlin.math.max
import kotlin.random.Random

/**
 * Green Task Scheduler will always chose the lowest energy spent per task not looking at queue sizes or idle times.
 * In order to control the dissemination of tasks, we need to implement deadlines
 *
 */

class GreenTaskScheduler(vararg devices: JayProto.Worker.Type) : AbstractScheduler("GreenTaskScheduler") {

    private var devices = devices.toList()
    private val offloadedTasks = LinkedHashMap<String, Pair<Long, Long?>>()
    private val lock = Object()
    private val offloadedLock = Object()

    @Suppress("DuplicatedCode")
    override fun init() {
        for (device in devices) JayLogger.logInfo("DEVICES", actions = arrayOf("DEVICE_TYPE=${device.name}"))
        if (JayProto.Worker.Type.REMOTE in devices) {
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
        } else {
            SchedulerService.broker.enableHeartBeats(getWorkerTypes()) {
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

    private fun greenSelection(workers: Set<JayProto.Worker>, task: Task, local: JayProto.Worker): Set<JayProto.Worker> {
        val possibleWorkers = mutableSetOf<JayProto.Worker>()
        var maxSpend = Float.NEGATIVE_INFINITY
        synchronized(lock) {
            workers.forEach { worker ->
                var spend: Float = getEnergySpentComputing(task, worker, local.powerEstimations)
                if (JaySettings.INCLUDE_IDLE_COSTS) spend += getIdleCost(task, worker, local)
                if (spend > 0) spend = spend.unaryMinus()
                JayLogger.logInfo("EXPECTED_ENERGY_SPENT_REMOTE", task.id, "WORKER=${worker.id}", "SPEND=$spend")
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

    private fun estimateCompletionTime(worker: JayProto.Worker, task: Task): Float {
        return (worker.queuedTasks + 1) * worker.avgTimePerTask + task.dataSize * worker.bandwidthEstimate +
                worker.avgResultSize * worker.bandwidthEstimate
    }

    private fun performanceSelection(workers: Set<JayProto.Worker>, task: Task): Set<JayProto.Worker> {
        val possibleWorkers = mutableSetOf<JayProto.Worker>()
        var faster = Float.POSITIVE_INFINITY
        synchronized(lock) {
            workers.forEach { worker ->
                val estimatedTime = estimateCompletionTime(worker, task)
                JayLogger.logInfo("EXPECTED_COMPLETION_TIME", task.id, "WORKER=${worker.id}", "TIME=$estimatedTime")
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
    override fun scheduleTask(task: Task): JayProto.Worker? {
        JayLogger.logInfo("BEGIN_SCHEDULING", task.id)
        val workers = SchedulerService.getWorkers(devices).values
        val meetDeadlines = mutableSetOf<JayProto.Worker>()
        val local = SchedulerService.getWorkers(JayProto.Worker.Type.LOCAL).values.elementAt(0)!!

        workers.forEach { worker ->
            if (SchedulerService.canMeetDeadline(task, worker) && worker != null) {
                meetDeadlines.add(worker)
            }
        }
        val possibleWorkers = if (meetDeadlines.isEmpty()) {
            JayLogger.logWarn("CANNOT_MEET_DEADLINE", task.id)
            workers.forEach { if (it != null) meetDeadlines.add(it) }
            when (JaySettings.TASK_DEADLINE_BROKEN_SELECTION) {
                "EXECUTE_LOCALLY" -> setOf(SchedulerService.getWorkers(JayProto.Worker.Type.LOCAL).values.first()!!)
                "FASTER_COMPLETION" -> performanceSelection(meetDeadlines, task)
                "RANDOM" -> meetDeadlines
                "LOWEST_ENERGY" -> greenSelection(meetDeadlines, task, local)
                else -> meetDeadlines
            }
        } else {
            greenSelection(meetDeadlines, task, local)
        }

        val w = if (possibleWorkers.isNotEmpty()) {
            possibleWorkers.elementAt(Random.nextInt(possibleWorkers.size))
        } else {
            workers.forEach { if (it != null) meetDeadlines.add(it) }
            meetDeadlines.elementAt(Random.nextInt(workers.size))
        }
        synchronized(offloadedLock) {
            offloadedTasks[task.id] = Pair(task.creationTimeStamp, task.deadline)
        }
        JayLogger.logInfo("COMPLETE_SCHEDULING", task.id, "WORKER=${w.id}")
        return w
    }


    private fun getIdleCost(task: Task, worker: JayProto.Worker, local: JayProto.Worker): Float {
        if (worker.id == local.id) {
            return 0.0f
        }
        val expectedTaskTime = ((worker.queuedTasks + 1) * worker.avgTimePerTask) +
                (worker.bandwidthEstimate.toLong() * task.dataSize) +
                (worker.avgResultSize * worker.bandwidthEstimate)
        val localComputingTime = (local.queuedTasks * local.avgTimePerTask)
        return ((max(0f, expectedTaskTime - localComputingTime) / 1000) / 3600) * local.powerEstimations.idle
    }

    private fun getEnergySpentComputing(task: Task, worker: JayProto.Worker, local: JayProto.PowerEstimations): Float {
        if (worker.powerEstimations.compute == 0.0f && worker.powerEstimations.idle == 0.0f
                && worker.powerEstimations.rx == 0.0f && worker.powerEstimations.tx == 0.0f
        ) return Float.NEGATIVE_INFINITY
        JayLogger.logInfo("ENERGY_SPENT_ESTIMATION_PARAMS", task.id,
                "AVG_TIME_PER_TASK=${worker.avgTimePerTask}",
                "BANDWIDTH_ESTIMATE=${worker.bandwidthEstimate}",
                "AVG_RESULT_SIZE=${worker.avgResultSize}",
                "POWER_REMOTE_COMPUTE=${worker.powerEstimations.compute}",
                "POWER_LOCAL_TX=${local.tx}",
                "POWER_REMOTE_RX=${worker.powerEstimations.rx}",
                "POWER_REMOTE_TX=${worker.powerEstimations.tx}"
        )
        return ((worker.avgTimePerTask / 1000f) / 3600f) * worker.powerEstimations.compute +
                if (worker.type == JayProto.Worker.Type.LOCAL) {
                    (((worker.bandwidthEstimate.toLong() * task.dataSize) / 1000f) / 3600) * local.tx +
                            (((worker.bandwidthEstimate.toLong() * task.dataSize) / 1000f) / 3600) * worker.powerEstimations.rx +
                            (((worker.avgResultSize * worker.bandwidthEstimate.toLong()) / 1000f) / 3600) * worker.powerEstimations.tx
                } else {
                    0f
                }
    }

    override fun destroy() {
        JayLogger.logInfo("INIT")
        JayLogger.logInfo("COMPLETE")
        super.destroy()
    }

    override fun getWorkerTypes(): JayProto.WorkerTypes {
        return JayUtils.genWorkerTypes(devices)
    }
}