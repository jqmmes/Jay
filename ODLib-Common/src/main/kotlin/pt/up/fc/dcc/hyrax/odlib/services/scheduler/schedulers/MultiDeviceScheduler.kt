package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.structures.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import kotlin.random.Random

class MultiDeviceScheduler(private val roundRobin: Boolean = false, vararg devices: ODProto.Worker.Type): Scheduler("MultiDeviceScheduler") {

    private var devices = devices.toList()
    private var roundRobinCount: Int = 0

    override fun init() {
        if (ODProto.Worker.Type.REMOTE in devices) {
            SchedulerService.listenForWorkers(true) {
                SchedulerService.enableHeartBeat(getWorkerTypes())
                super.init()
            }
        } else {
            SchedulerService.enableHeartBeat(getWorkerTypes())
            super.init()
        }
    }

    override fun getName(): String {
        val strategy = if (roundRobin) "RoundRobin" else "Random"
        var devs = ""
        for (devType in devices) devs += "$devType, "
        return "${super.getName()} [$strategy] [${devs.trimEnd(' ', ',')}]"
    }

    override fun scheduleJob(job: ODJob) : ODProto.Worker? {
        println(devices)
        val workers = SchedulerService.getWorkers(devices)
        return when {
            workers.isEmpty() -> null
            roundRobin -> {
                println(roundRobinCount)
                println(workers.size)
                roundRobinCount %= workers.size
                workers.values.toTypedArray()[roundRobinCount++]
            }
            else -> workers.values.toTypedArray()[Random.nextInt(workers.values.size - 1)]
        }
    }

    override fun destroy() {
        devices = emptyList()
        SchedulerService.disableHeartBeat()
        roundRobinCount = 0
        if (ODProto.Worker.Type.REMOTE in devices) {
            SchedulerService.listenForWorkers(false)
        }
    }

    override fun getWorkerTypes(): ODProto.WorkerTypes {
        return ODUtils.genWorkerTypes(devices)
    }
}