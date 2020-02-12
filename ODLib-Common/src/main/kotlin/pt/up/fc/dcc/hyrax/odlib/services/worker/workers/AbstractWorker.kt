package pt.up.fc.dcc.hyrax.odlib.services.worker.workers

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.WorkerJob

abstract class AbstractWorker<T> {

    protected abstract var workerName: String

    open fun init(vararg params: Any?) {

    }

    abstract fun executeJob(task: WorkerJob?, callback: ((T) -> Unit)?)

    abstract fun setSetting(key: String, value: Any?, statusCallback: ((ODProto.Status) -> Unit)? = null)

    open fun setSettings(settingsMap: Map<String, Any?>) {
        for (k in settingsMap.keys) {
            setSetting(k, settingsMap[k])
        }
    }

    abstract fun <V> callAction(action: String, statusCallback: ((ODProto.Status) -> Unit)? = null, vararg args: Any): V

    abstract fun runAction(action: String, statusCallback: ((ODProto.Status) -> Unit)? = null, vararg args: Any)

    abstract fun destroy()
}