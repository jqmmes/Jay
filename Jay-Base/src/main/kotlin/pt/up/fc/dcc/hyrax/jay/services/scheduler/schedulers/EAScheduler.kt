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

class EAScheduler(vararg devices: JayProto.Worker.Type) : AbstractScheduler("EAScheduler") {

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
     *
     * In the future, I should account for my own energy in this calculation.
     * Variations:
     *  -> Energy spent per task per device
     *  -> Energy spent per task, globaly (includes my energy offloading and receiving and waiting)
     *  -> Energy spent globaly on all devices I know
     *
     * Addon:
     *   -> Deal with energy and include deadlines.
     *
     * Future Work:
     *   -> Deal with Task Failures
     */
    override fun scheduleTask(task: Task): JayProto.Worker? {
        JayLogger.logInfo("BEGIN_SCHEDULING", task.id)
        val workers = SchedulerService.getWorkers(devices).values
        val currentLatch = CountDownLatch(workers.size)
        val currentMap = mutableMapOf<JayProto.Worker, JayProto.CurrentEstimations?>()
        val possibleWorkers = mutableSetOf<JayProto.Worker>()
        val lock = Object()
        workers.forEach { worker ->
            executionPool.submit {
                SchedulerService.getExpectedCurrent(worker) {
                    JayLogger.logInfo("GOT_EXPECTED_CURRENT", task.id,
                            "WORKER=${worker?.id}",
                            "BAT_CAPACITY=${it?.batteryCapacity}",
                            "BAT_LEVEL=${it?.batteryLevel}",
                            "BAT_COMPUTE=${it?.compute}",
                            "BAT_IDLE=${it?.idle}",
                            "BAT_TX=${it?.tx}",
                            "BAT_RX=${it?.rx}")
                    synchronized(lock) {
                        currentMap[worker!!] = it
                    }
                    currentLatch.countDown()
                }
            }
        }
        currentLatch.await()
        var minSpend = Float.MIN_VALUE
        synchronized(lock) {
            currentMap.forEach { (w, c) ->
                val spend: Float = getEnergySpentComputing(task, w, c)
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
    private fun getEnergySpentComputing(task: Task, worker: JayProto.Worker, current: JayProto.CurrentEstimations?): Float {
        return worker.avgTimePerTask * ((current?.compute ?: 0f) / 3600) +
                worker.bandwidthEstimate.toLong() * task.dataSize.toLong() * ((current?.rx ?: 0f) / 3600) +
                worker.queueSize * worker.avgTimePerTask * ((current?.compute ?: 0f) / 3600) +
                worker.avgResultSize * worker.bandwidthEstimate.toLong() * ((current?.tx ?: 0f) / 3600)
    }

    /*
    todo:
        If host is remote, I need to account with MY OWN queue*current.compute*avgTimePerTask +
        (((remote.queue + 1) * remote.avgTimePerTask) - queue*avgTimePerTask) * current.idle

    private fun getEnergySpentRemoteComputingAndLocalIdle(task: Task, worker: JayProto.Worker, current: JayProto.CurrentEstimations?): Long {
        return worker.avgTimePerTask * ((current?.compute ?: 0) / 3600)+
                worker.bandwidthEstimate.toLong() * task.dataSize.toLong() * ((current?.rx ?: 0) / 3600) +
                worker.queueSize * worker.avgTimePerTask * ((current?.compute ?: 0) / 3600) +
                worker.avgResultSize * worker.bandwidthEstimate.toLong() * ((current?.tx ?: 0) / 3600)
    }

    todo:
        If I want to calculate the energy spent on the whole neighborhood of devices I need to
        check what is the consequence of running a task on each different device and take into account
        the energy spend with each one queue size and also idle
    private fun getEnergySpentRemoteGlobal(task: Task, worker: JayProto.Worker, current: JayProto
    .CurrentEstimations?): Long {
        return worker.avgTimePerTask * ((current?.compute ?: 0) / 3600)+
                worker.bandwidthEstimate.toLong() * task.dataSize.toLong() * ((current?.rx ?: 0) / 3600) +
                worker.queueSize * worker.avgTimePerTask * ((current?.compute ?: 0) / 3600) +
                worker.avgResultSize * worker.bandwidthEstimate.toLong() * ((current?.tx ?: 0) / 3600)
    }

    todo:
        Add an option to check if a task can be completed within a timeout
     */

    override fun destroy() {
        JayLogger.logInfo("INIT")
        JayLogger.logInfo("COMPLETE")
        super.destroy()
    }

    override fun getWorkerTypes(): JayProto.WorkerTypes {
        return JayUtils.genWorkerTypes(devices)
    }
}