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

package pt.up.fc.dcc.hyrax.droid_jay_app.tensorfow_task

import android.content.Context
import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.droid_jay_app.tensorfow_task.tensorflow.Detection
import pt.up.fc.dcc.hyrax.droid_jay_app.tensorfow_task.tensorflow.DroidTensorflow
import pt.up.fc.dcc.hyrax.droid_jay_app.tensorfow_task.tensorflow.DroidTensorflowLite
import pt.up.fc.dcc.hyrax.droid_jay_app.tensorfow_task.tensorflow.Model
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayTensorFlowProto
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.TaskExecutor
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.droid_jay_app.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.jay.proto.JayTensorFlowProto.Model as JayModel

class TensorflowTaskExecutor(
    private val context: Context,
    name: String = "Tensorflow",
    description: String? = null,
    private val lite: Boolean = false
) : TaskExecutor(name, description) {

    private lateinit var classifier: DetectObjects

    override fun init(vararg params: Any?) {
        classifier = if (lite) DroidTensorflowLite(context) else DroidTensorflow(context)
    }

    private fun genDetection(detection: Detection?): JayTensorFlowProto.Detection {
        return JayTensorFlowProto.Detection.newBuilder()
                .setClass_(detection!!.class_)
                .setScore(detection.score)
                .build()
    }

    override fun executeTask(task: Task, callback: ((Any) -> Unit)?) {
        try {
            JayLogger.logInfo("READ_IMAGE_DATA", task.info.getId())
            val imgData = task.data

                JayLogger.logInfo("START", task.info.getId())
            val results = JayTensorFlowProto.Results.newBuilder()
            var resultsStr = ""
            for (detection in classifier.detectObjects(imgData)) {
                resultsStr += "${detection.class_}(${detection.score}),"
                results.addDetections(genDetection(detection))
            }
            JayLogger.logInfo("TASK_RESULTS", task.info.getId(), "VALUES=${resultsStr.trimEnd(',')}")
            callback?.invoke(results.build().toByteString())
        } catch (e: Exception) {
            e.printStackTrace()
            JayLogger.logError("FAIL", task.info.getId())
            callback?.invoke(JayTensorFlowProto.Results.getDefaultInstance().toByteString())
        }
    }

    override fun setSetting(key: String, value: Any?, statusCallback: ((Boolean) -> Unit)?) {
        when (key) {
            "GPU" -> {
                classifier.useGPU = true
                statusCallback?.invoke(true)
            }
            "CPU" -> {
                classifier.useGPU = false
                statusCallback?.invoke(true)
            }
            "NNAPI" -> {
                classifier.useNNAPI = true
                statusCallback?.invoke(true)
            }
            else -> statusCallback?.invoke(false)
        }
    }

    private fun genModelRequest(listModels: Set<Model>): JayTensorFlowProto.Models? {
        val models = JayTensorFlowProto.Models.newBuilder()
        for (model in listModels) {
            models.addModels(model.getProto())
        }
        return models.build()
    }

    override fun callAction(action: String, statusCallback: ((Boolean, Any?) -> Unit)?, vararg args: Any) {
        when (action) {
            "listModels" -> statusCallback?.invoke(true, genModelRequest(classifier.models.toSet())?.toByteArray())
            else -> {
                statusCallback?.invoke(false, ByteArray(0))
                throw(NoSuchElementException("Unknown Action: $action"))
            }
        }
    }

    override fun runAction(action: String, statusCallback: ((Boolean) -> Unit)?, vararg args: Any) {
        when (action) {
            "loadModel" -> {
                if (args.isEmpty()) {
                    statusCallback?.invoke(false)
                    throw RuntimeException("loadModel requires a ByteString Model arg")
                }
                if (args[0] !is ByteString) {
                    statusCallback?.invoke(false)
                    throw RuntimeException("Invalid loadModel arg type (${args[0].javaClass.name}")
                }
                val model = JayModel.parseFrom(args[0] as ByteString)
                classifier.loadModel(Model(model!!.id, model.name, model.url, model.downloaded, model.isQuantized, model.inputSize), statusCallback)
            }
            else -> {
                statusCallback?.invoke(false)
                throw RuntimeException("Unknown Action: $action")
            }
        }
    }

    override fun getDefaultResponse(callback: ((Any) -> Unit)?) {
        callback?.invoke(JayTensorFlowProto.Results.getDefaultInstance().toByteString())
    }

    override fun getQueueSize(): Int {
        return 20
    }
}