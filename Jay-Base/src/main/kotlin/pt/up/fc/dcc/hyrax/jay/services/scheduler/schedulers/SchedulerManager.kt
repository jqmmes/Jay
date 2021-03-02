package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.structures.Scheduler

object SchedulerManager {
    internal val schedulers: MutableSet<AbstractScheduler> = mutableSetOf()
    internal var scheduler: AbstractScheduler? = null

    fun registerScheduler(scheduler: AbstractScheduler) {
        schedulers.add(scheduler)
    }

    fun removeScheduler(scheduler: Scheduler) {
        schedulers.removeIf { s ->
            when (s.id) {
                scheduler.id -> true
                else -> false
            }
        }
    }

    internal fun destroy() {
        schedulers.clear()
        try {
            scheduler?.destroy()
        } finally {
            scheduler = null
        }
    }

    internal fun listSchedulers(): JayProto.Schedulers {
        JayLogger.logInfo("INIT")
        val schedulersProto = JayProto.Schedulers.newBuilder()
        for (scheduler in schedulers) {
            JayLogger.logInfo("SCHEDULER_INFO", actions = arrayOf("SCHEDULER_NAME=${scheduler.getName()}", "SCHEDULER_ID=${scheduler.id}"))
            schedulersProto.addScheduler(scheduler.getProto())
        }
        JayLogger.logInfo("COMPLETE")
        return schedulersProto.build()
    }

    internal fun setScheduler(id: String?): JayProto.StatusCode {
        for (scheduler in schedulers)
            if (scheduler.id == id) {
                this.scheduler?.destroy()
                this.scheduler = scheduler
                this.scheduler?.init()
                this.scheduler?.waitInit()
                return JayProto.StatusCode.Success
            }
        return JayProto.StatusCode.Error
    }
}