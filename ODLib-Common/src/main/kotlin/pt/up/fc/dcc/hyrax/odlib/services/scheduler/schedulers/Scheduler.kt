package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.structures.ODJob
import java.util.*
import java.util.concurrent.CountDownLatch

abstract class Scheduler(name: String) {

    val id: String = UUID.randomUUID().toString()
    private val nameId: String = name
    private var waitInit = CountDownLatch(1)

    open fun getName() : String {
        return nameId
    }

    abstract fun scheduleJob(job: ODJob) : ODProto.Worker?

    abstract fun getWorkerTypes() : ODProto.WorkerTypes

    open fun init() {
        waitInit.countDown()
    }

    open fun destroy() {
        waitInit = CountDownLatch(1)
    }

    fun getProto(): ODProto.Scheduler {
        return ODProto.Scheduler.newBuilder().setId(id).setName(getName()).build()
    }

    fun waitInit() {
        waitInit.await()
    }
}