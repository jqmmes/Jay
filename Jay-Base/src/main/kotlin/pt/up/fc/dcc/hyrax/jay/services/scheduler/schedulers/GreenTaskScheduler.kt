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
 * This scheduler should control readings from Profiler. Samples can be obtained via:
 * - Random Sampling
 * - On<Action> (OnBoot, OnScheduler, onLocalScheduling (Control task completion) or onOffloading
 *
 * Sampling should be processed using JayStates and Battery/Load/CPU usage factors
 *
 */

class GreenTaskScheduler(vararg devices: JayProto.Worker.Type) : AbstractScheduler("EAScheduler") {

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
        val lock = Object()
        workers.forEach { worker ->
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
        }
        powerLatch.await()
        var minSpend = Long.MIN_VALUE
        synchronized(lock) {
            powerMap.forEach { (w, c) ->
                val spend: Long = getEnergySpentComputing(task, w, c)
                JayLogger.logInfo("BATTERY_SPENT_REMOTE", task.id, "WORKER=${w.id}", "SPEND=$spend")
                //energy spend is negative, so higher value the better
                if (spend > minSpend) {
                    possibleWorkers.clear()
                    minSpend = spend
                    possibleWorkers.add(w)
                } else if (spend == minSpend) {
                    possibleWorkers.add(w)
                }
            }
        }
        val w = possibleWorkers.elementAt((Random.nextInt(possibleWorkers.size)))
        JayLogger.logInfo("COMPLETE_SCHEDULING", task.id, "WORKER=${w.id}")
        return w
    }

    // This only takes into account the energy spent on computing host
    private fun getEnergySpentComputing(task: Task, worker: JayProto.Worker, oower: JayProto.PowerEstimations?): Long {
        return worker.avgTimePerTask * ((oower?.compute ?: 0) / 3600) +
                worker.bandwidthEstimate.toLong() * task.dataSize.toLong() * ((oower?.rx ?: 0) / 3600) +
                worker.queueSize * worker.avgTimePerTask * ((oower?.compute ?: 0) / 3600) +
                worker.avgResultSize * worker.bandwidthEstimate.toLong() * ((oower?.tx ?: 0) / 3600)
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