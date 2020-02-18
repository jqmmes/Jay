package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors

import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto.WorkerJob

abstract class AbstractTaskExecutor<T> {

    protected abstract var workerName: String

    open fun init(vararg params: Any?) {

    }

    abstract fun executeJob(task: WorkerJob?, callback: ((T) -> Unit)?)

    abstract fun setSetting(key: String, value: Any?, statusCallback: ((JayProto.Status) -> Unit)? = null)

    open fun setSettings(settingsMap: Map<String, Any?>) {
        for (k in settingsMap.keys) {
            setSetting(k, settingsMap[k])
        }
    }

    abstract fun <V> callAction(action: String, statusCallback: ((JayProto.Status) -> Unit)? = null, vararg args: Any): V

    abstract fun runAction(action: String, statusCallback: ((JayProto.Status) -> Unit)? = null, vararg args: Any)

    abstract fun destroy()
}