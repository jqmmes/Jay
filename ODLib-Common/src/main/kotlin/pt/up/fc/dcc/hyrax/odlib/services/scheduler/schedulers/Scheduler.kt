package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
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

    open fun init() {
        println("Super init")
        waitInit.countDown()
        println("Super init -1")
    }

    abstract fun destroy()

    fun getProto(): ODProto.Scheduler {
        return ODProto.Scheduler.newBuilder().setId(id).setName(getName()).build()
    }

    fun waitInit() {
        println("Wait init")
        waitInit.await()
        println("Wait ended")
    }
}