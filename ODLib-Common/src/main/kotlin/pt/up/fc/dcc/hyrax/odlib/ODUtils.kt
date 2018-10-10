package pt.up.fc.dcc.hyrax.odlib

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.ODClient
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.enums.ReturnStatus
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import java.lang.NullPointerException

class ODUtils {

    @Suppress("unused")
    class ODDetection(val score : Float, val class_: Int, val box : Box)

    class Box

    companion object {
        internal fun parseResults(results: ODProto.JobResults?): List<ODDetection?> {
            try {
                val detections: Array<ODDetection?> = arrayOfNulls(results!!.detectionsCount)
                var i = 0
                for (detection in results.detectionsList) {
                    detections[i++] = ODDetection(detection.score, detection.class_, Box())
                }
                return detections.asList()
            } catch (e: NullPointerException) {
                ODLogger.logError("parseResults Exception: (${e.message})")
                for (message in e.stackTrace) ODLogger.logError(message.toString())
            }
            return emptyList()
        }

        private fun genDetection(detection: ODUtils.ODDetection?) : ODProto.Detection{
            return ODProto.Detection.newBuilder()
                    .setClass_(detection!!.class_)
                    .setScore(detection.score)
                    .build()
        }

        internal fun genResults(id: Long, results: List<ODUtils.ODDetection?>) : ODProto.JobResults {
            val builder = ODProto.JobResults.newBuilder()
                    .setId(id)
            for (detection in results) {
                builder.addDetections(genDetection(detection))
            }
            return builder.build()
        }

        internal fun genStatus(status: ReturnStatus) : ODProto.Status {
            return ODProto.Status.newBuilder().setCode(status.code).build()
        }

        private fun genModel(model : ODModel) : ODProto.Model {
            return ODProto.Model.newBuilder()
                    .setId(model.modelId)
                    .setName(model.modelName)
                    .build()
        }

        internal fun parseModel(model: ODProto.Model?) : ODModel {
            return ODModel(model!!.id, model.name)
        }

        internal fun genJobRequest(imgId: Long, imgData : ByteArray) : ODProto.Job {
            return ODProto.Job.newBuilder().setId(imgId).setData(ByteString.copyFrom(imgData)).build()
        }

        internal fun parseModelConfig(modelConfig: ODProto.ModelConfig?) : Pair<ODModel, HashMap<String, String>> {
            return Pair(parseModel(modelConfig!!.model), HashMap(modelConfig.configsMap))
        }

        internal fun parseAsyncRequestImageByteArray(asyncRequest: ODProto.AsyncRequest?) : ByteArray {
            return asyncRequest!!.job.data.toByteArray()
        }

        internal fun parseAsyncRequestRemoteClient(asyncRequest: ODProto.AsyncRequest?) : RemoteODClient? {
            return ClientManager.getRemoteODClient(asyncRequest!!.remoteClient.id)
            //return AbstractODLib.getClient(asyncRequest!!.remoteClient.address, asyncRequest.remoteClient.port)
        }

        @Suppress("unused")
        internal fun genModelConfig(model: ODModel, configs: Map<String, String>) : ODProto.ModelConfig {
            return ODProto.ModelConfig.newBuilder()
                    .putAllConfigs(configs)
                    .setModel(genModel(model))
                    .build()
        }

        internal fun genRemoteClient(remoteODClient: ODClient) : ODProto.RemoteClient{
            return ODProto.RemoteClient.newBuilder()
                    .setAddress(remoteODClient.getAddress())
                    .setPort(remoteODClient.getPort())
                    .build()

        }

        fun genAsyncRequest(id: Long, data: ByteArray, remoteClient: ODClient): ODProto.AsyncRequest? {
            return  ODProto.AsyncRequest.newBuilder()
                    .setJob(genJobRequest(id, data))
                    .setRemoteClient(genRemoteClient(remoteClient))
                    .build()
        }

        fun parseModels(result: ODProto.Models?): Set<ODModel> {
            val parsedResults : HashSet<ODModel> = HashSet()
            var model : ODModel
            for (x in 0..result!!.modelsCount) {
                model = parseModel(result.getModels(x))
                parsedResults.add(ODModel(model.modelId, model.modelName))
            }
            return parsedResults.toSet()
        }

        fun genModels(listModels: Set<ODModel>): ODProto.Models {
            val builder = ODProto.Models.newBuilder()
            for (iterator in listModels)
                builder.addModels(genModel(iterator))
            return builder.build()
        }
    }
}