package pt.up.fc.dcc.hyrax.odlib.services.worker.workers

import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.WorkerJob
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.structures.Model

class TensorflowWorker<T>(override var workerName: String) : AbstractWorker<T>() {

    private lateinit var classifier: DetectObjects

    override fun init(vararg params: Any?) {
        if (params.isEmpty()) throw Error("Invalid number of init args")
        if (params[0] !is DetectObjects) throw Error("Arg[0] should be of DetectObjects type")
        classifier = params[0] as DetectObjects
    }

    @Suppress("UNCHECKED_CAST")
    override fun executeJob(task: WorkerJob?, callback: ((T) -> Unit)?) {
        try {
            ODLogger.logInfo("READ_IMAGE_DATA", task?.id ?: "")
            val imgData = WorkerService.fsAssistant?.readTempFile(task?.fileId) ?: ByteArray(0)
            ODLogger.logInfo("START", task?.id ?: "")
            callback?.invoke(classifier.detectObjects(imgData) as T)
        } catch (e: Exception) {
            e.printStackTrace()
            ODLogger.logError("FAIL", task?.id ?: "")
            callback?.invoke(emptyList<T>() as T)
        }
    }

    override fun setSetting(key: String, value: Any?, statusCallback: ((ODProto.Status) -> Unit)?) {}

    @Suppress("UNCHECKED_CAST")
    override fun <V> callAction(action: String, statusCallback: ((ODProto.Status) -> Unit)?, vararg args: Any): V {
        when (action) {
            "listModels" -> return classifier.models.toSet() as V
        }
        return Unit as V
    }

    override fun runAction(action: String, statusCallback: ((ODProto.Status) -> Unit)?, vararg args: Any) {
        when (action) {
            "loadModel" -> {
                if (args.isEmpty()) throw Error()
                if (args[0] !is Model) throw Error()
                classifier.loadModel(args[0] as Model, statusCallback)
            }
        }
    }

    override fun destroy() {}
}