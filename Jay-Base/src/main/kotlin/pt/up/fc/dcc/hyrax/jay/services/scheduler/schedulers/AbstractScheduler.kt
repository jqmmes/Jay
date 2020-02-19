package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.structures.Job
import java.util.*
import java.util.concurrent.CountDownLatch

abstract class AbstractScheduler(name: String) {

    val id: String = UUID.randomUUID().toString()
    private val nameId: String = name
    private var waitInit = CountDownLatch(1)

    open fun getName(): String {
        return nameId
    }

    abstract fun scheduleJob(job: Job): JayProto.Worker?

    abstract fun getWorkerTypes(): JayProto.WorkerTypes

    open fun init() {
        JayLogger.logInfo("INIT", actions = *arrayOf("SCHEDULER_ID=$id", "SCHEDULER_NAME=$nameId"))
        waitInit.countDown()
    }

    open fun destroy() {
        waitInit = CountDownLatch(1)
    }

    fun getProto(): JayProto.Scheduler {
        return JayProto.Scheduler.newBuilder().setId(id).setName(getName()).build()
    }

    fun waitInit() {
        waitInit.await()
    }
}