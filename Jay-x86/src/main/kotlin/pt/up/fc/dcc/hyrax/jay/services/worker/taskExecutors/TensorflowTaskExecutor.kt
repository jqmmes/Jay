package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.tensorflow.CloudletTensorFlow
import pt.up.fc.dcc.hyrax.jay.structures.Model
import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils

class TensorflowTaskExecutor(name: String = "Tensorflow", description: String? = null, private val fsAssistant: FileSystemAssistant?) : TaskExecutor(name, description) {

    private val classifier = CloudletTensorFlow()

    override fun executeJob(task: JayProto.WorkerJob?, callback: ((Any) -> Unit)?) {
        try {
            JayLogger.logInfo("READ_IMAGE_DATA", task?.id ?: "")
            val imgData = fsAssistant?.readTempFile(task?.fileId) ?: ByteArray(0)
            JayLogger.logInfo("START", task?.id ?: "")
            callback?.invoke(classifier.detectObjects(imgData))
        } catch (e: Exception) {
            e.printStackTrace()
            JayLogger.logError("FAIL", task?.id ?: "")
            callback?.invoke(emptyList<Any>())
        }
    }

    override fun setSetting(key: String, value: Any?, statusCallback: ((JayProto.Status) -> Unit)?) {}

    override fun callAction(action: String, statusCallback: ((JayProto.Status, Any?) -> Unit)?, vararg args: Any) {
        when (action) {
            "listModels" -> statusCallback?.invoke(JayUtils.genStatusSuccess()!!, JayUtils.genModelRequest(classifier.models.toSet())?.toByteArray())
        }
    }

    override fun runAction(action: String, statusCallback: ((JayProto.Status) -> Unit)?, vararg args: Any) {
        when (action) {
            "loadModel" -> {
                if (args.isEmpty()) throw Error()
                if (args[0] !is Model) throw Error()
                classifier.loadModel(args[0] as Model, statusCallback)
            }
        }
    }
}