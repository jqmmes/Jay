package pt.up.fc.dcc.hyrax.odlib.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.utils.ODJob
import java.util.*

abstract class Scheduler(name: String) {

    val id: String = UUID.randomUUID().toString()
    private val nameId: String = name

    open fun getName() : String {
        return nameId
    }

    abstract fun scheduleJob(job: ODJob) : ODProto.Worker?

    open fun init() {}

    abstract fun destroy()

    fun getProto(): ODProto.Scheduler {
        return ODProto.Scheduler.newBuilder().setId(id).setName(getName()).build()
    }
}