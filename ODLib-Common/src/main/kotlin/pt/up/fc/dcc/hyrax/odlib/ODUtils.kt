package pt.up.fc.dcc.hyrax.odlib

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.odlib.interfaces.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.interfaces.ReturnStatus
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto

class ODUtils {

    class ODDetection(val score : Float, val class_: Int, val box : Box) {}

    class Box(){}

    companion object {
        internal fun parseResults(results: ODProto.Results?): List<ODDetection?> {
            val detections : Array<ODDetection?> = arrayOfNulls(results!!.detectionsCount)
            var i = 0
            for (detection in results.detectionsList) {
                detections[i++] = ODDetection(detection.score, detection.class_, Box())
            }
            return detections.asList()
        }

        private fun genDetection(detection: ODUtils.ODDetection?) : ODProto.Detection{
            return ODProto.Detection.newBuilder()
                    .setClass_(detection!!.class_)
                    .setScore(detection.score)
                    .build()
        }

        internal fun genResults(id: Int, results: List<ODUtils.ODDetection?>) : ODProto.Results {
            val builder = ODProto.Results.newBuilder()
                    .setId(id)
            for (detection in results) {
                builder.addDetections(genDetection(detection))
            }
            return builder.build()
        }

        internal fun genStatus(status: ReturnStatus) : ODProto.Status {
            return ODProto.Status.newBuilder().setCode(status.code).build()
        }

        internal fun genModel(model : ODModel) : ODProto.Model {
            return ODProto.Model.newBuilder()
                    .setId(model.modelId)
                    .setName(model.modelName)
                    .build()
        }

        internal fun parseModel(model: ODProto.Model?) : ODModel {
            val odModel = ODModel()
            odModel.modelId = model!!.id
            odModel.modelName = model.name
            return odModel
        }

        internal fun genImageRequest(imgId: Int, imgData : ByteArray) : ODProto.Image {
            return ODProto.Image.newBuilder().setId(imgId).setData(ByteString.copyFrom(imgData)).build()
        }

        internal fun parseModelConfig(modelConfig: ODProto.ModelConfig?) : Pair<ODModel, HashMap<String, String>> {
            return Pair(parseModel(modelConfig!!.model), HashMap(modelConfig.configsMap))
        }

        internal fun parseAsyncRequestImageByteArray(asyncRequest: ODProto.AsyncRequest?) : ByteArray {
            return asyncRequest!!.image.data.toByteArray()
        }

        internal fun parseAsyncRequestRemoteClient(asyncRequest: ODProto.AsyncRequest?) : RemoteODClient? {
            return AbstractODLib.getClient(asyncRequest!!.remoteClient.address, asyncRequest.remoteClient.port)
        }

        internal fun genModelConfig(model: ODModel, configs: Map<String, String>) : ODProto.ModelConfig {
            return ODProto.ModelConfig.newBuilder()
                    .putAllConfigs(configs)
                    .setModel(genModel(model))
                    .build()
        }

        internal fun genRemoteClient(remoteODClient: ODClient) : ODProto.RemoteClient{
            return ODProto.RemoteClient.newBuilder()
                    .setAddress(remoteODClient.getAdress())
                    .setPort(remoteODClient.getPort())
                    .build()

        }

        fun genAsyncRequest(id: Int, data: ByteArray, remoteClient: ODClient): ODProto.AsyncRequest? {
            return  ODProto.AsyncRequest.newBuilder()
                    .setImage(genImageRequest(id, data))
                    .setRemoteClient(genRemoteClient(remoteClient))
                    .build()
        }
    }
}