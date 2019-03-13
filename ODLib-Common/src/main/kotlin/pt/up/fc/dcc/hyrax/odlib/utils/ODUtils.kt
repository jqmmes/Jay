package pt.up.fc.dcc.hyrax.odlib.utils

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto

object ODUtils {

    /*internal fun parseResults(results: ODProto.Results?): List<ODDetection?> {
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
    } */

    private fun genDetection(detection: ODDetection?) : ODProto.Detection{
        return ODProto.Detection.newBuilder()
                .setClass_(detection!!.class_)
                .setScore(detection.score)
                .build()
    }

    internal fun genResults(id: String, results: List<ODDetection?>) : ODProto.Results {
        val builder = ODProto.Results.newBuilder()
                .setId(id)
        for (detection in results) {
            builder.addDetections(genDetection(detection))
        }
        return builder.build()
    }

    /*internal fun genStatus(code: ReturnStatus) : ODProto.RequestStatus {
        return ODProto.RequestStatus.newBuilder().setCode(ODProto.RequestStatus.Code.forNumber(code.code)).build()
    }*/

    /*internal fun genModel(model : ODModel) : ODProto.Model {
        return ODProto.Model.newBuilder()
                .setId(model.modelId)
                .setName(model.modelName)
                .setUrl(model.remoteUrl)
                .setDownloaded(model.downloaded)
                .build()
    }

    internal fun parseModel(model: ODProto.Model?) : ODModel {
        return ODModel(model!!.id, model.name, model.url, model.downloaded)
    }*/

    /*internal fun parseModelConfig(modelConfig: ODProto.ModelConfig?) : Pair<ODModel, HashMap<String, String>> {
        return Pair(parseModel(modelConfig!!.model), HashMap(modelConfig.configsMap))
    }

    internal fun parseAsyncRequestImageByteArray(asyncRequest: ODProto.AsyncRequest?) : ByteArray {
        return asyncRequest!!.job.data.toByteArray()
    }*/

    /*internal fun parseAsyncRequestRemoteClient(asyncRequest: ODProto.AsyncRequest?) : RemoteODClient? {
        val clientId = if (NetworkUtils.getLocalIpV4(false) == asyncRequest!!.remoteClient.address) 0 else ODUtils.genClientId(asyncRequest.remoteClient.address)
        return ClientManager.getRemoteODClient(clientId)
    }*/

    internal fun genModelConfig(configs: Map<String, String>) : ODProto.ModelConfig {
        return ODProto.ModelConfig.newBuilder()
                .putAllConfigs(configs)
                .build()
    }

    /*fun parseModels(result: ODProto.Models?): Set<ODModel> {
        val parsedResults : HashSet<ODModel> = HashSet()
        for (rawModel in 0 until result!!.modelsCount) {
            parsedResults.add(parseModel(result.getModels(rawModel)))
        }
        return parsedResults.toSet()
    }

    fun genModels(listModels: Set<ODModel>): ODProto.Models {
        val builder = ODProto.Models.newBuilder()
        for (iterator in listModels)
            builder.addModels(genModel(iterator))
        return builder.build()
    }*/

    fun genDeviceStatus(deviceInformation: DeviceInformation) : ODProto.Worker {
        val deviceStatus = ODProto.Worker.newBuilder()
        deviceStatus.battery = deviceInformation.battery
        deviceStatus.batteryStatus = ODProto.Worker.BatteryStatus.CHARGED// TODO: Load appropriate status
        deviceStatus.cpuCores = deviceInformation.computationThreads
        deviceStatus.queueSize = deviceInformation.queueSize
        deviceStatus.runningJobs = deviceInformation.runningJobs
        //deviceStatus.pendingJobs = deviceInformation.pendingJobs
        //deviceStatus.connections = deviceInformation.connections
        return deviceStatus.build()

    }

    /*fun parseDeviceStatus(deviceStatus: ODProto.Worker) : DeviceInformation {
        val deviceInformation = DeviceInformation()
        deviceInformation.battery = deviceStatus.battery
        deviceInformation.batteryStatus.code = deviceStatus.batteryStatus
        deviceInformation.computationThreads = deviceStatus.cpuCores
        deviceInformation.queueSize = deviceStatus.queueSize
        deviceInformation.runningJobs = deviceStatus.runningJobs
        //deviceInformation.pendingJobs = deviceStatus.pendingJobs
        //deviceInformation.connections= deviceStatus.connections
        return deviceInformation
    }*/

    /*fun parseDeviceStatus(data: ByteArray): DeviceInformation {
        val deviceStatus = ODProto.Worker.parseFrom(data)
        return parseDeviceStatus(deviceStatus)
    }*/

    fun genModelRequest(listModels: Set<ODModel>): ODProto.Models? {
        val models = ODProto.Models.newBuilder()
        for (model in listModels) {
            models.addModels(model.getProto())
        }
        return models.build()
    }

    fun parseModels(models: ODProto.Models?): Set<ODModel> {
        val modelSet : MutableSet<ODModel> = mutableSetOf()
        for (model in models!!.modelsList) {
            modelSet.add(ODModel(model))
        }
        return modelSet.toSet()
    }
}
