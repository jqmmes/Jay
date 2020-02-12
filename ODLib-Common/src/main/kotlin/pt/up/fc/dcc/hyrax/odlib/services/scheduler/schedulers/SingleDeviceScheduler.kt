package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.odlib.structures.Job
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

class SingleDeviceScheduler(private val workerType: ODProto.Worker.Type) : AbstractScheduler("SingleDeviceScheduler") {

    private var worker: ODProto.Worker? = null

    override fun init() {
        ODLogger.logInfo("WORKER_TYPE=${workerType.name}")
        if (workerType != ODProto.Worker.Type.LOCAL) {
            SchedulerService.listenForWorkers(true) {
                ODLogger.logInfo("COMPLETE")
                SchedulerService.enableHeartBeat(getWorkerTypes()) { super.init() }
            }
        } else {
            SchedulerService.enableHeartBeat(getWorkerTypes()) {super.init()}
        }
    }

    override fun getName(): String {
        return "${super.getName()} [${workerType.name}]"
    }

    override fun scheduleJob(job: Job) : ODProto.Worker? {
        ODLogger.logInfo("INIT", job.id, actions = *arrayOf("WORKER_ID=${worker?.id}"))
        if (worker == null) {
            for (w in SchedulerService.getWorkers(workerType).values) {
                if (w?.type == workerType) {
                    worker = w
                    break
                }
            }
        }
        ODLogger.logInfo("COMPLETE", job.id, actions = *arrayOf("WORKER_ID=${worker?.id}"))
        return worker
    }

    override fun destroy() {
        ODLogger.logInfo("INIT")
        worker = null
        SchedulerService.disableHeartBeat()
        if (workerType == ODProto.Worker.Type.REMOTE) {
            SchedulerService.listenForWorkers(false)
        }
        ODLogger.logInfo("COMPLETE")
        super.destroy()
    }

    override fun getWorkerTypes(): ODProto.WorkerTypes {
        return ODUtils.genWorkerTypes(workerType)
    }
}