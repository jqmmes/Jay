package pt.up.fc.dcc.hyrax.odlib.utils

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.DeviceInformation
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.enums.ReturnStatus
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import java.lang.NullPointerException

class ODUtils {

    class ODDetection(val score : Float, val class_: Int)

    companion object {
        internal fun parseResults(results: ODProto.JobResults?): List<ODDetection?> {
            try {
                val detections: Array<ODDetection?> = arrayOfNulls(results!!.detectionsCount)
                var i = 0
                for (detection in results.detectionsList) {
                    detections[i++] = ODDetection(detection.score, detection.class_)
                }
                return detections.asList()
            } catch (e: NullPointerException) {
                ODLogger.logError("parseResults Exception: (${e.message})")
                for (message in e.stackTrace) ODLogger.logError(message.toString())
            }
            return emptyList()
        }

        private fun genDetection(detection: ODDetection?) : ODProto.Detection{
            return ODProto.Detection.newBuilder()
                    .setClass_(detection!!.class_)
                    .setScore(detection.score)
                    .build()
        }

        internal fun genResults(id: Long, results: List<ODDetection?>) : ODProto.JobResults {
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

        internal fun genModel(model : ODModel) : ODProto.Model {
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
            val clientId = if (NetworkUtils.getLocalIpV4() == asyncRequest!!.remoteClient.address) 0 else ODUtils.genClientId(asyncRequest.remoteClient.address)
            return ClientManager.getRemoteODClient(clientId)
        }

        internal fun genModelConfig(configs: Map<String, String>) : ODProto.ModelConfig {
            return ODProto.ModelConfig.newBuilder()
                    .putAllConfigs(configs)
                    .build()
        }

        internal fun genLocalClient(): ODProto.RemoteClient? {
            return ODProto.RemoteClient.newBuilder()
                    .setAddress(NetworkUtils.getLocalIpV4())
                    .setPort(ODSettings.serverPort)
                    .setId(ODUtils.genClientId(NetworkUtils.getLocalIpV4()))
                    .build()
        }

        internal fun genClientId(ipV4: String): Long {
            try {
                return if (NetworkUtils.getLocalIpV4() == ipV4 || ipV4 == "localhost") 0 else ipV4.replace(".", "")
                        .toLong()
            } catch (e: Exception ) {
                ODLogger.logWarn("failed to gen id from $ipV4")
            }
            return 0
        }

        fun genAsyncRequest(id: Long, data: ByteArray): ODProto.AsyncRequest? {
            return  ODProto.AsyncRequest.newBuilder()
                    .setJob(genJobRequest(id, data))
                    .setRemoteClient(genLocalClient())
                    .build()
        }

        fun parseModels(result: ODProto.Models?): Set<ODModel> {
            val parsedResults : HashSet<ODModel> = HashSet()
            var model : ODModel
            for (x in 0 until result!!.modelsCount) {
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

        fun genDeviceStatus(deviceInformation: DeviceInformation) : ODProto.DeviceStatus {
            val deviceStatus = ODProto.DeviceStatus.newBuilder()
            deviceStatus.battery = deviceInformation.battery
            deviceStatus.batteryStatus = deviceInformation.batteryStatus.ordinal
            deviceStatus.cpuCores = deviceInformation.computationThreads
            deviceStatus.queueSize = deviceInformation.queueSize
            deviceStatus.currentJobs = deviceInformation.runningJobs + deviceInformation.pendingJobs
            deviceStatus.connections = deviceInformation.connections
            return deviceStatus.build()
        }


        fun parseDeviceStatus(data: ByteArray): DeviceInformation {
            val deviceStatus = ODProto.DeviceStatus.parseFrom(data)
            val deviceInformation = DeviceInformation()
            deviceInformation.battery = deviceStatus.battery
            //deviceInformation.batteryStatus = BatteryStatus.(deviceStatus.batteryStatus)
            deviceInformation.computationThreads = deviceStatus.cpuCores
            deviceInformation.queueSize = deviceStatus.queueSize
            deviceInformation.runningJobs = Math.max(deviceStatus.currentJobs-deviceStatus.cpuCores, 0)
            deviceInformation.pendingJobs = Math.max(deviceStatus.currentJobs-deviceInformation.runningJobs, 0)
            deviceInformation.connections= deviceStatus.connections
            return deviceInformation
        }
    }
}