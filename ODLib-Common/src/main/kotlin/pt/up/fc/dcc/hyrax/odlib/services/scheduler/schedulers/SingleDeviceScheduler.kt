package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob

class SingleDeviceScheduler(private val workerType: ODProto.Worker.Type) : Scheduler("SingleDeviceScheduler") {

    private var worker : ODProto.Worker? = null

    override fun init() {
        super.init()
        if (workerType == ODProto.Worker.Type.REMOTE) {
            SchedulerService.listenForWorkers(true)
        }
    }

    override fun getName(): String {
        return "${super.getName()} [${workerType.name}]"
    }

    override fun scheduleJob(job: ODJob) : ODProto.Worker? {
        if (worker == null) {
            for (w in SchedulerService.getWorkers().values) {
                println(w?.type)
                if (w?.type == workerType) {
                    worker = w
                    break
                }
            }
        }
        return worker
    }

    override fun destroy() {
        worker = null
        if (workerType == ODProto.Worker.Type.REMOTE) {
            SchedulerService.listenForWorkers(false)
        }
    }
}