package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JayThreadPoolExecutor
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.concurrent.CountDownLatch

/**
 * todo: deal with 0 values. Should know how many entries were used to calc estimation
 *
 * This scheduler should control readings from Profiler. Samples can be obtained via:
 * - Random Sampling
 * - On<Action> (OnBoot, OnScheduler, onLocalScheduling (Control task completion) or onOffloading
 *
 * Sampling should be processed using JayStates and Battery/Load/CPU usage factors
 *
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

    override fun scheduleTask(task: Task): JayProto.Worker? {
        var chosenWorker: JayProto.Worker? = null
        val workers = SchedulerService.getWorkers(devices).values
        val currentLatch = CountDownLatch(workers.size)
        val currentMap = mutableMapOf<JayProto.Worker, JayProto.CurrentEstimations?>()
        workers.forEach { worker ->
            executionPool.submit {
                SchedulerService.getExpectedCurrent(worker) {
                    currentMap[worker!!] = it
                    currentLatch.countDown()
                }
            }
        }
        currentLatch.await()
        var minSpend = Long.MIN_VALUE
        currentMap.forEach { (t, u) ->
            val spend = t.avgTimePerTask * (u?.compute ?: 0)
            if (spend > minSpend) {
                minSpend = spend
                chosenWorker = t
            }
        }
        return chosenWorker
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