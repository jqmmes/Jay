package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.*
import java.util.concurrent.CountDownLatch

abstract class AbstractScheduler(name: String) {

    val id: String = UUID.randomUUID().toString()
    private val nameId: String = name
    private var waitInit = CountDownLatch(1)

    open fun getName(): String {
        return nameId
    }

    abstract fun scheduleTask(task: Task): JayProto.Worker?
    abstract fun getWorkerTypes(): JayProto.WorkerTypes
    open fun setSetting(key: String, value: Any?, statusCallback: ((JayProto.Status) -> Unit)? = null) {
        statusCallback?.invoke(JayUtils.genStatusError())
    }

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

    open fun setSettings(settingsMap: Map<String, Any?>): JayProto.Status {
        var status = JayUtils.genStatusSuccess()
        for (k in settingsMap.keys) {
            setSetting(k, settingsMap[k]) { setting_status ->
                if (setting_status.code == JayProto.StatusCode.Error) {
                    status = JayUtils.genStatusError()
                }
            }
        }
        return status
    }
}