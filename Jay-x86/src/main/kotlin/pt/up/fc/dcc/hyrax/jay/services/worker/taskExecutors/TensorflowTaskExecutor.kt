/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayTensorFlowProto
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.tensorflow.CloudletTensorFlow
import pt.up.fc.dcc.hyrax.jay.structures.Detection
import pt.up.fc.dcc.hyrax.jay.structures.Model
import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import pt.up.fc.dcc.hyrax.jay.proto.JayTensorFlowProto.Detection as JayDetection
import pt.up.fc.dcc.hyrax.jay.proto.JayTensorFlowProto.Model as JayModel
import pt.up.fc.dcc.hyrax.jay.proto.JayTensorFlowProto.Results as JayResults

class TensorflowTaskExecutor(name: String = "Tensorflow", description: String? = null, private val fsAssistant: FileSystemAssistant?) : TaskExecutor(name, description) {

    private val classifier = CloudletTensorFlow()

    private fun genDetection(detection: Detection?): JayDetection {
        return JayDetection.newBuilder()
                .setClass_(detection!!.class_)
                .setScore(detection.score)
                .build()
    }

    override fun executeTask(task: JayProto.WorkerTask?, callback: ((Any) -> Unit)?) {
        try {
            JayLogger.logInfo("READ_IMAGE_DATA", task?.id ?: "")
            val imgData = fsAssistant?.readTempFile(task?.fileId) ?: ByteArray(0)
            JayLogger.logInfo("START", task?.id ?: "")
            val results = JayResults.newBuilder()
            for (detection in classifier.detectObjects(imgData)) {
                results.addDetections(genDetection(detection))
            }
            callback?.invoke(results.build().toByteString())
        } catch (e: Exception) {
            e.printStackTrace()
            JayLogger.logError("FAIL", task?.id ?: "")
            callback?.invoke(JayResults.getDefaultInstance().toByteString())
        }
    }

    override fun setSetting(key: String, value: Any?, statusCallback: ((JayProto.Status) -> Unit)?) {}

    private fun genModelRequest(listModels: Set<Model>): JayTensorFlowProto.Models? {
        val models = JayTensorFlowProto.Models.newBuilder()
        for (model in listModels) {
            models.addModels(model.getProto())
        }
        return models.build()
    }

    override fun getQueueSize(): Int {
        return 20
    }

    override fun callAction(action: String, statusCallback: ((JayProto.Status, Any?) -> Unit)?, vararg args: Any) {
        when (action) {
            "listModels" -> statusCallback?.invoke(JayUtils.genStatusSuccess(), genModelRequest(classifier.models.toSet())?.toByteArray())
            else -> {
                statusCallback?.invoke(JayUtils.genStatusError(), ByteArray(0))
                throw(NoSuchElementException("Unknown Action: $action"))
            }
        }
    }

    private fun genErrorWithCallback(callback: ((JayProto.Status) -> Unit)?, error: Throwable) {
        callback?.invoke(JayUtils.genStatusError())
        throw error
    }

    override fun runAction(action: String, statusCallback: ((JayProto.Status) -> Unit)?, vararg args: Any) {
        when (action) {
            "loadModel" -> {
                if (args.isEmpty()) genErrorWithCallback(statusCallback,
                        RuntimeException("loadModel requires a ByteString Model arg"))
                if (args[0] !is ByteString) genErrorWithCallback(statusCallback,
                        RuntimeException("Invalid loadModel arg type (${args[0].javaClass.name}"))
                val model = JayModel.parseFrom(args[0] as ByteString)
                classifier.loadModel(Model(model!!.id, model.name, model.url, model.downloaded, model.isQuantized, model.inputSize), statusCallback)
            }
            else -> genErrorWithCallback(statusCallback, NoSuchElementException("Unknown Action: $action"))
        }
    }

    override fun getDefaultResponse(callback: ((Any) -> Unit)?) {
        callback?.invoke(JayTensorFlowProto.Results.getDefaultInstance().toByteString())
    }
}