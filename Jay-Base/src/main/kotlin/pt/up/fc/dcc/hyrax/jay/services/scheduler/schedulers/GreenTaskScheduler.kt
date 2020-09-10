package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayThreadPoolExecutor
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

/**
 * Green Task Scheduler will always chose the lowest energy spent per task not looking at queue sizes or idle times.
 * In order to control the dissemination of tasks, we need to implement deadlines
 *
 */

class GreenTaskScheduler(vararg devices: JayProto.Worker.Type) : AbstractScheduler("GreenTaskScheduler") {

    private var devices = devices.toList()
    private val executionPool = JayThreadPoolExecutor(10)
    private val offloadedTasks = LinkedHashMap<String, Pair<Long, Long?>>()

    @Suppress("DuplicatedCode")
    override fun init() {
        for (device in devices) JayLogger.logInfo("DEVICES", actions = arrayOf("DEVICE_TYPE=${device.name}"))
        if (JayProto.Worker.Type.REMOTE in devices) {
            SchedulerService.listenForWorkers(true) {
                SchedulerService.broker.enableHeartBeats(getWorkerTypes()) {
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
            if (offloadedTasks.containsKey(taskId)) {
                if (offloadedTasks[taskId]?.second != null) {
                    val deltaTask = (System.currentTimeMillis() - offloadedTasks[taskId]!!.first) / 1000f
                    JayLogger.logInfo("TASK_WITH_DEADLINE_COMPLETED", taskId,
                            "DEADLINE_MET=${deltaTask <= offloadedTasks[taskId]!!.second!!}")
                }
                offloadedTasks.remove(taskId)
            }
        }
    }

    override fun getName(): String {
        var devices = ""
        for (devType in this.devices) devices += "$devType, "
        return "${super.getName()} [${devices.trimEnd(' ', ',')}]"
    }

    private fun getPowerMap(workers: Set<JayProto.Worker?>, task: Task, skipDeadline: Boolean = false):
            MutableMap<JayProto.Worker, JayProto.PowerEstimations?> {
        val powerMap = mutableMapOf<JayProto.Worker, JayProto.PowerEstimations?>()
        val lock = Object()
        val powerLatch = CountDownLatch(workers.size)

        workers.forEach { worker ->
            if (canMeetDeadline(task, worker) || skipDeadline) {
                executionPool.submit {
                    SchedulerService.getExpectedPower(worker) {
                        JayLogger.logInfo("GOT_EXPECTED_POWER", task.id,
                                "WORKER=${worker?.id}",
                                "BAT_CAPACITY=${it?.batteryCapacity}",
                                "BAT_LEVEL=${it?.batteryLevel}",
                                "BAT_COMPUTE=${it?.compute}",
                                "BAT_IDLE=${it?.idle}",
                                "BAT_TX=${it?.tx}",
                                "BAT_RX=${it?.rx}")
                        synchronized(lock) {
                            powerMap[worker!!] = it
                        }
                        powerLatch.countDown()
                    }
                }
            } else {
                powerLatch.countDown()
            }
        }
        powerLatch.await()
        return powerMap
    }

    /**
     * In here we have to estimate how much energy will be spent on the remote device
     * i.e. task receive, queue time (remote device will be working), compute, result send
     */
    override fun scheduleTask(task: Task): JayProto.Worker? {
        JayLogger.logInfo("BEGIN_SCHEDULING", task.id)
        val workers = SchedulerService.getWorkers(devices).values
        var powerMap = getPowerMap(workers.toSet(), task)
        val possibleWorkers = mutableSetOf<JayProto.Worker>()
        val localExpectedPower = SchedulerService.profiler.getExpectedPower()
        val lock = Object()

        if (powerMap.isEmpty()) {
            JayLogger.logWarn("CANNOT_MEET_DEADLINE", task.id)
            when (JaySettings.TASK_DEADLINE_BROKEN_SELECTION) {
                "EXECUTE_LOCALLY" -> return SchedulerService.getWorkers(JayProto.Worker.Type.LOCAL).values.first()
                // "FASTER_COMPLETION" -> null // todo
                "LOWEST_ENERGY" -> powerMap = getPowerMap(workers.toSet(), task, true)
            }
        }
        var maxSpend = Float.MAX_VALUE
        synchronized(lock) {
            powerMap.forEach { (worker, powerEstimations) ->
                // we receive negative power values from profiler, so we negate them to be more readable. We want the minimum consumption
                val spend: Float = (if (worker.type == JayProto.Worker.Type.LOCAL) {
                    (worker.avgTimePerTask / 3600f) * (powerEstimations?.compute ?: 0f)
                } else {
                    getEnergySpentComputing(task, worker, powerEstimations, localExpectedPower)
                }).unaryMinus()
                JayLogger.logInfo("EXPECTED_ENERGY_SPENT_REMOTE", task.id, "WORKER=${worker.id}", "SPEND=$spend")
                if (spend < maxSpend) {
                    possibleWorkers.clear()
                    maxSpend = spend
                    possibleWorkers.add(worker)
                } else if (spend == maxSpend) {
                    possibleWorkers.add(worker)
                }
            }
        }
        val w = possibleWorkers.elementAt((Random.nextInt(possibleWorkers.size)))
        JayLogger.logInfo("COMPLETE_SCHEDULING", task.id, "WORKER=${w.id}")
        offloadedTasks[task.id] = Pair(System.currentTimeMillis(), task.deadline)
        return w
    }

    private fun canMeetDeadline(task: Task, worker: JayProto.Worker?): Boolean {
        if (worker == null) return false
        if (task.deadlineDuration == null) return true
        if (((worker.queueSize + 1) * (worker.avgTimePerTask / 1000f)) <= task.deadlineDuration)
            return true
        return false
    }

    private fun getEnergySpentComputing(task: Task, worker: JayProto.Worker, power: JayProto.PowerEstimations?, local: JayProto.PowerEstimations?): Float {
        return ((worker.avgTimePerTask / 1000f) / 3600f) * (power?.compute ?: 0f) +
                (((worker.bandwidthEstimate.toLong() * task.dataSize.toLong()) / 1000f) / 3600) * (local?.tx ?: 0f) +
                (((worker.bandwidthEstimate.toLong() * task.dataSize.toLong()) / 1000f) / 3600) * (power?.rx ?: 0f) +
                (((worker.avgResultSize * worker.bandwidthEstimate.toLong()) / 1000f) / 3600) * (power?.tx ?: 0f)
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