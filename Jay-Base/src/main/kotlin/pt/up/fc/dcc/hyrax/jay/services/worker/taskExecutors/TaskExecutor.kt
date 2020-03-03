package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors

import pt.up.fc.dcc.hyrax.jay.proto.JayProto.StatusCode
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.WorkerJob
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils.genStatusError
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils.genStatusSuccess
import java.util.UUID.randomUUID
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Status as JayStatus

abstract class TaskExecutor(val name: String, val description: String?) {

    val id: String = randomUUID().toString()

    open fun init(vararg params: Any?) {}
    open fun destroy() {}

    abstract fun executeJob(task: WorkerJob?, callback: ((Any) -> Unit)?)
    abstract fun setSetting(key: String, value: Any?, statusCallback: ((JayStatus) -> Unit)? = null)
    abstract fun callAction(action: String, statusCallback: ((JayStatus, Any?) -> Unit)? = null, vararg args: Any)
    abstract fun runAction(action: String, statusCallback: ((JayStatus) -> Unit)? = null, vararg args: Any)

    open fun setSettings(settingsMap: Map<String, Any?>): JayStatus {
        var status = genStatusSuccess()!!
        for (k in settingsMap.keys) {
            println("SET_TASK_EXECUTOR_SETTING $k: ${settingsMap[k]}")
            setSetting(k, settingsMap[k]) { setting_status ->
                if (setting_status.code == StatusCode.Error) {
                    status = genStatusError()!!
                }
            }
        }
        return status
    }
}