package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JayThreadPoolExecutor
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

/**
 * Green Task Scheduler will always chose the lowest energy spent per task not looking at queue sizes or idle times.
 * In order to control the dessimination of tasks, we need to implement deadlines
 *
 */

class GreenTaskScheduler(vararg devices: JayProto.Worker.Type) : AbstractScheduler("GreenTaskScheduler") {

    private var devices = devices.toList()
    private val executionPool = JayThreadPoolExecutor(10)

    override fun init() {
        for (device in devices) JayLogger.logInfo("DEVICES", actions = *arrayOf("DEVICE_TYPE=${device.name}"))
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
    }

    override fun getName(): String {
        var devices = ""
        for (devType in this.devices) devices += "$devType, "
        return "${super.getName()} [${devices.trimEnd(' ', ',')}]"
    }

    /**
     * In here we have to estimate how much energy will be spent on the remote device
     * i.e. task receive, queue time (remote device will be working), compute, result send
     */
    override fun scheduleTask(task: Task): JayProto.Worker? {
        JayLogger.logInfo("BEGIN_SCHEDULING", task.id)
        val workers = SchedulerService.getWorkers(devices).values
        val powerLatch = CountDownLatch(workers.size)
        val powerMap = mutableMapOf<JayProto.Worker, JayProto.PowerEstimations?>()
        val possibleWorkers = mutableSetOf<JayProto.Worker>()
        val localExpectedPower = SchedulerService.profiler.getExpectedPower()
        val lock = Object()
        workers.forEach { worker ->
            if (canMeetDeadline(task, worker)) {
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
        var maxSpend = Float.MAX_VALUE
        synchronized(lock) {
            powerMap.forEach { (worker, powerEstimations) ->
                // we receive negative power values from profiler, so we negate them to be more readable. We want the minimum consumption
                val spend: Float = (if (worker.type == JayProto.Worker.Type.LOCAL) {
                    (worker.avgTimePerTask / 3600f) * (powerEstimations?.compute ?: 0f)
                } else {
                    getEnergySpentComputing(task, worker, powerEstimations, localExpectedPower)
                }).unaryMinus()
                JayLogger.logInfo("EXPECTED_BATTERY_SPENT_REMOTE", task.id, "WORKER=${worker.id}", "SPEND=$spend")
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
        return w
    }

    private fun canMeetDeadline(task: Task, worker: JayProto.Worker?): Boolean {
        if (worker == null) return false
        if (task.deadline == null) return true
        if (((worker.queueSize + 1) * (worker.avgTimePerTask / 1000f)) <= task.deadline)
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