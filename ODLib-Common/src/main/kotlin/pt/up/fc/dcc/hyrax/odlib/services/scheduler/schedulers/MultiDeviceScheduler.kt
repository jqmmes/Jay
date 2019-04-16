package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.structures.Job
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import kotlin.random.Random

class MultiDeviceScheduler(private val roundRobin: Boolean = false, vararg devices: ODProto.Worker.Type): Scheduler("MultiDeviceScheduler") {

    private var devices = devices.toList()
    private var roundRobinCount: Int = 0

    override fun init() {
        ODLogger.logInfo("MultiDeviceScheduler, INIT, ${if (roundRobin) "ROUND_ROBIN" else "RANDOM"}")
        for (device in devices) ODLogger.logInfo("MultiDeviceScheduler, INIT, DEVICE_TYPE=${device.name}")
        if (ODProto.Worker.Type.REMOTE in devices) {
            SchedulerService.listenForWorkers(true) {
                SchedulerService.enableHeartBeat(getWorkerTypes()) {super.init()}
            }
        } else {
            SchedulerService.enableHeartBeat(getWorkerTypes()) {super.init()}
        }
    }

    override fun getName(): String {
        val strategy = if (roundRobin) "RoundRobin" else "Random"
        var devs = ""
        for (devType in devices) devs += "$devType, "
        return "${super.getName()} [$strategy] [${devs.trimEnd(' ', ',')}]"
    }

    override fun scheduleJob(job: Job) : ODProto.Worker? {
        ODLogger.logInfo("MultiDeviceScheduler, SCHEDULE_JOB, JOB_ID=${job.id}")
        val workers = SchedulerService.getWorkers(devices)
        return when {
            workers.isEmpty() -> null
            roundRobin -> {
                roundRobinCount %= workers.size
                workers.values.toTypedArray()[roundRobinCount++]
            }
            else -> workers.values.toTypedArray()[Random.nextInt(workers.values.size - 1)]
        }
    }

    override fun destroy() {
        ODLogger.logInfo("MultiDeviceScheduler, DESTROY")
        devices = emptyList()
        SchedulerService.disableHeartBeat()
        roundRobinCount = 0
        if (ODProto.Worker.Type.REMOTE in devices) {
            SchedulerService.listenForWorkers(false)
        }
        super.destroy()
    }

    override fun getWorkerTypes(): ODProto.WorkerTypes {
        return ODUtils.genWorkerTypes(devices)
    }
}