package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Job
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import kotlin.random.Random

class MultiDeviceScheduler(private val roundRobin: Boolean = false, vararg devices: JayProto.Worker.Type) : AbstractScheduler("MultiDeviceScheduler") {

    private var devices = devices.toList()
    private var roundRobinCount: Int = 0

    override fun init() {
        JayLogger.logInfo("INIT", actions = *arrayOf("SCHEDULE_STRATEGY=${if (roundRobin) "ROUND_ROBIN" else "RANDOM"}"))
        for (device in devices) JayLogger.logInfo("DEVICES", actions = *arrayOf("DEVICE_TYPE=${device.name}"))
        if (JayProto.Worker.Type.REMOTE in devices) {
            SchedulerService.listenForWorkers(true) {
                SchedulerService.enableHeartBeat(getWorkerTypes()) {
                    JayLogger.logInfo("COMPLETE")
                    super.init()
                }
            }
        } else {
            SchedulerService.enableHeartBeat(getWorkerTypes()) {
                JayLogger.logInfo("COMPLETE")
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

    override fun scheduleJob(job: Job): JayProto.Worker? {
        JayLogger.logInfo("INIT", job.id)
        val workers = SchedulerService.getWorkers(devices)
        val worker = when {
            workers.isEmpty() -> null
            roundRobin -> {
                roundRobinCount %= workers.size
                workers.values.toTypedArray()[roundRobinCount++]
            }
            else -> workers.values.toTypedArray()[if (workers.values.size > 1) Random.nextInt(workers.values.size) else 0]
        }
        JayLogger.logInfo("COMPLETE", actions = *arrayOf("WORKER_ID=${worker?.id}"))
        return worker
    }

    override fun destroy() {
        JayLogger.logInfo("INIT")
        devices = emptyList()
        SchedulerService.disableHeartBeat()
        roundRobinCount = 0
        if (JayProto.Worker.Type.REMOTE in devices) {
            SchedulerService.listenForWorkers(false)
        }
        JayLogger.logInfo("COMPLETE")
        super.destroy()
    }

    override fun getWorkerTypes(): JayProto.WorkerTypes {
        return JayUtils.genWorkerTypes(devices)
    }
}