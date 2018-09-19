package pt.up.fc.dcc.hyrax.odlib

import com.google.protobuf.ByteString
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

        internal fun genResults(id: Int, results: List<ODUtils.ODDetection?>) : ODProto.Results {
            val builder = ODProto.Results.newBuilder()
            for (detection in results) {

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

        internal fun genModelConfig(model: ODModel, configs: Map<String, String>) : ODProto.ModelConfig {
            return ODProto.ModelConfig.newBuilder()
                    .putAllConfigs(configs)
                    .setModel(genModel(model))
                    .build()
        }

        /*internal fun genEmpty() : ODProto.Empty? {
            return ODProto.Empty.getDefaultInstance()
        }*/
    }
}