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
        ODLogger.logInfo("INIT",actions = *arrayOf("SCHEDULE_STRATEGY=${if (roundRobin) "ROUND_ROBIN" else "RANDOM"}"))
        for (device in devices) ODLogger.logInfo("DEVICES",actions = *arrayOf("DEVICE_TYPE=${device.name}"))
        if (ODProto.Worker.Type.REMOTE in devices) {
            SchedulerService.listenForWorkers(true) {
                SchedulerService.enableHeartBeat(getWorkerTypes()) {
                    ODLogger.logInfo("COMPLETE")
                    super.init()
                }
            }
        } else {
            SchedulerService.enableHeartBeat(getWorkerTypes()) {
                ODLogger.logInfo("COMPLETE")
                super.init()
            }
        }
    }

    override fun getName(): String {
        val strategy = if (roundRobin) "RoundRobin" else "Random"
        var devices = ""
        for (devType in this.devices) devices += "$devType, "
        return "${super.getName()} [$strategy] [${devices.trimEnd(' ', ',')}]"
    }

    override fun scheduleJob(job: Job) : ODProto.Worker? {
        ODLogger.logInfo("INIT", job.id)
        val workers = SchedulerService.getWorkers(devices)
        val worker = when {
            workers.isEmpty() -> null
            roundRobin -> {
                roundRobinCount %= workers.size
                workers.values.toTypedArray()[roundRobinCount++]
            }
            else -> workers.values.toTypedArray()[if (workers.values.size > 1) Random.nextInt(workers.values.size) else 0]
        }
        ODLogger.logInfo("COMPLETE", actions = *arrayOf("WORKER_ID=${worker?.id}"))
        return worker
    }

    override fun destroy() {
        ODLogger.logInfo("INIT")
        devices = emptyList()
        SchedulerService.disableHeartBeat()
        roundRobinCount = 0
        if (ODProto.Worker.Type.REMOTE in devices) {
            SchedulerService.listenForWorkers(false)
        }
        ODLogger.logInfo("COMPLETE")
        super.destroy()
    }

    override fun getWorkerTypes(): ODProto.WorkerTypes {
        return ODUtils.genWorkerTypes(devices)
    }
}