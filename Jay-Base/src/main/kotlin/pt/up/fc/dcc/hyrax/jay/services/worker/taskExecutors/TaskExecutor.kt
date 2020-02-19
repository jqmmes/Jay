package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors

import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.WorkerJob
import java.util.*

abstract class TaskExecutor(val name: String, val description: String?) {

    val id: String = UUID.randomUUID().toString()

    open fun init(vararg params: Any?) {}
    open fun destroy() {}

    abstract fun executeJob(task: WorkerJob?, callback: ((Any) -> Unit)?)
    abstract fun setSetting(key: String, value: Any?, statusCallback: ((JayProto.Status) -> Unit)? = null)
    abstract fun callAction(action: String, statusCallback: ((JayProto.Status, Any?) -> Unit)? = null, vararg args: Any)
    abstract fun runAction(action: String, statusCallback: ((JayProto.Status) -> Unit)? = null, vararg args: Any)

    open fun setSettings(settingsMap: Map<String, Any?>) {
        for (k in settingsMap.keys) {
            setSetting(k, settingsMap[k])
        }
    }
}