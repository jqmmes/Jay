package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.interfaces.ReturnStatus
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto

class ODUtils {

    class ODDetection(val score : Float, val class_: Int, val box : Box) {}

    class Box(){}

    companion object {
        internal fun parseResults(results: ODProto.Results): List<ODDetection?> {
            val detections : Array<ODDetection?> = arrayOfNulls(results.detectionsCount)
            var i = 0
            for (detection in results.detectionsList) {
                detections[i++] = ODDetection(detection.score, detection.class_, Box())
            }
            return detections.asList()
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

        internal fun parseModelConfig(modelConfig: ODProto.ModelConfig?) : Pair<ODModel, HashMap<String, String>> {
            return Pair(parseModel(modelConfig!!.model), HashMap(modelConfig.configsMap))
        }

        internal fun genModelConfig(model: ODModel, configs: Map<String, String>) : ODProto.ModelConfig {
            return ODProto.ModelConfig.newBuilder()
                    .putAllConfigs(configs)
                    .setModel(genModel(model))
                    .build()
        }

        internal fun genEmpty() : ODProto.Empty {
            return ODProto.Empty.newBuilder().build()
        }
    }
}