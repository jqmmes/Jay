package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils

/**
 * todo: implement EAScheduler
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

    override fun init() {
    }

    override fun getName(): String {
        return "${super.getName()} [$devices}]"
    }

    override fun scheduleTask(task: Task): JayProto.Worker? {
        return (SchedulerService.getWorkers(devices).values).elementAt(0)
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