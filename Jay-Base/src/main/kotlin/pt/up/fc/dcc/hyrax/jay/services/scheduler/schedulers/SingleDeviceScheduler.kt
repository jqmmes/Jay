package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils

class SingleDeviceScheduler(private val workerType: JayProto.Worker.Type) : AbstractScheduler("SingleDeviceScheduler") {

    private var worker: JayProto.Worker? = null

    override fun init() {
        JayLogger.logInfo("WORKER_TYPE=${workerType.name}")
        if (workerType != JayProto.Worker.Type.LOCAL) {
            SchedulerService.listenForWorkers(true) {
                JayLogger.logInfo("COMPLETE")
                SchedulerService.broker.enableHeartBeats(getWorkerTypes()) { super.init() }
            }
        } else {
            SchedulerService.broker.enableHeartBeats(getWorkerTypes()) { super.init() }
        }
    }

    override fun getName(): String {
        return "${super.getName()} [${workerType.name}]"
    }

    override fun scheduleTask(task: Task): JayProto.Worker? {
        JayLogger.logInfo("INIT", task.id, actions = arrayOf("WORKER_ID=${worker?.id}"))
        if (worker == null) {
            for (w in SchedulerService.getWorkers(workerType).values) {
                if (w?.type == workerType) {
                    worker = w
                    break
                }
            }
        }
        JayLogger.logInfo("COMPLETE", task.id, actions = arrayOf("WORKER_ID=${worker?.id}"))
        return worker
    }

    override fun destroy() {
        JayLogger.logInfo("INIT")
        worker = null
        SchedulerService.broker.disableHeartBeats()
        if (workerType == JayProto.Worker.Type.REMOTE) {
            SchedulerService.listenForWorkers(false)
        }
        JayLogger.logInfo("COMPLETE")
        super.destroy()
    }

    override fun getWorkerTypes(): JayProto.WorkerTypes {
        return JayUtils.genWorkerTypes(workerType)
    }
}