package pt.up.fc.dcc.hyrax.jay.utils

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Worker.Type
import pt.up.fc.dcc.hyrax.jay.structures.Detection
import pt.up.fc.dcc.hyrax.jay.structures.Model
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.NetworkInterface

object JayUtils {

    private var localIPv4: String = ""
    private var firstRefresh: Boolean = true

    internal inline fun <reified T> getCompatibleInterfaces(): List<NetworkInterface> {
        val interfaceList: MutableList<NetworkInterface> = mutableListOf()
        for (netInt in NetworkInterface.getNetworkInterfaces()) {
            if (!netInt.isLoopback && !netInt.isPointToPoint && netInt.isUp && netInt.supportsMulticast()) {
                for (address in netInt.inetAddresses)
                    if (address is T) {
                        if (JaySettings.MCAST_INTERFACE == null || (JaySettings.MCAST_INTERFACE != null && netInt.name ==
                                        JaySettings.MCAST_INTERFACE)) {
                            JayLogger.logInfo("INTERFACE_AVAILABLE", actions = *arrayOf("INTERFACE=${netInt.name}"))
                            interfaceList.add(netInt)
                        }
                    }
            }
        }
        return interfaceList
    }

    fun getLocalIpV4(refresh: Boolean = false): String {
        if (refresh || firstRefresh) {
            firstRefresh = false
            localIPv4 = ""
            val interfaces = getCompatibleInterfaces<Inet4Address>()
            if (interfaces.isNotEmpty()) {
                for (ip in interfaces[0].inetAddresses) {
                    if (ip is Inet4Address) localIPv4 = ip.toString().trim('/')
                }
            }
        }
        return localIPv4
    }

    fun getHostAddressFromPacket(packet: DatagramPacket): String {
        return packet.address.hostAddress.substringBefore("%")
    }

    private fun genDetection(detection: Detection?): JayProto.Detection {
        return JayProto.Detection.newBuilder()
                .setClass_(detection!!.class_)
                .setScore(detection.score)
                .build()
    }

    internal fun genResults(id: String, results: List<Detection?>): JayProto.Results {
        val builder = JayProto.Results.newBuilder()
                .setId(id)
        for (detection in results) {
            builder.addDetections(genDetection(detection))
        }
        return builder.build()
    }

    fun genModelRequest(listModels: Set<Model>): JayProto.Models? {
        val models = JayProto.Models.newBuilder()
        for (model in listModels) {
            models.addModels(model.getProto())
        }
        return models.build()
    }

    fun genStatus(code: JayProto.StatusCode): JayProto.Status? {
        return JayProto.Status.newBuilder().setCode(code).build()
    }

    fun parseModels(models: JayProto.Models?): Set<Model> {
        val modelSet: MutableSet<Model> = mutableSetOf()
        for (model in models!!.modelsList) {
            modelSet.add(Model(model))
        }
        return modelSet.toSet()
    }

    fun parseSchedulers(schedulers: JayProto.Schedulers?): Set<Pair<String, String>> {
        val schedulerSet: MutableSet<Pair<String, String>> = mutableSetOf()
        for (scheduler in schedulers!!.schedulerList) schedulerSet.add(Pair(scheduler.id, scheduler.name))
        return schedulerSet
    }

    fun getJobDetails(job: JayProto.Job?): JayProto.JobDetails? {
        if (job == null) return JayProto.JobDetails.getDefaultInstance()
        return JayProto.JobDetails.newBuilder().setId(job.id).setDataSize(job.data.size()).build()
    }

    fun genWorkerTypes(vararg types: Type): JayProto.WorkerTypes {
        return JayProto.WorkerTypes.newBuilder().addAllType(types.asIterable()).build()
    }

    fun genWorkerTypes(types: List<Type>): JayProto.WorkerTypes {
        return JayProto.WorkerTypes.newBuilder().addAllType(types).build()
    }

    fun genStatusSuccess(): JayProto.Status? {
        return genStatus(JayProto.StatusCode.Success)
    }

    fun genStatusError(): JayProto.Status? {
        return genStatus(JayProto.StatusCode.Success)
    }

    fun genWorkerProto(id: String? = null, batteryLevel: Int, batteryCurrent: Int, batteryVoltage: Int,
                       batteryTemperature: Float, batteryEnergy: Long, batteryCharge: Int,
                       avgComputingEstimate: Long, cpuCores: Int, queueSize: Int,
                       queuedJobs: Int, runningJobs: Int, type: Type? = null,
                       bandwidthEstimate: Float? = null, totalMemory: Long,
                       freeMemory: Long): JayProto.Worker? {
        val worker = JayProto.Worker.newBuilder()
        if (id != null) worker.id = id // Internal
        worker.batteryLevel = batteryLevel // Modified by Worker
        worker.batteryCurrent = batteryCurrent
        worker.batteryVoltage = batteryVoltage
        worker.batteryTemperature = batteryTemperature
        worker.batteryEnergy = batteryEnergy
        worker.batteryCharge = batteryCharge
        worker.avgTimePerJob = avgComputingEstimate // Modified by Worker
        worker.cpuCores = cpuCores // Set by Worker
        worker.queueSize = queueSize // Set by Worker
        worker.queuedJobs = queuedJobs
        worker.runningJobs = runningJobs // Modified by Worker
        if (type != null) worker.type = type // Set in Broker
        if (bandwidthEstimate != null) worker.bandwidthEstimate = bandwidthEstimate // Set internally
        worker.totalMemory = totalMemory
        worker.freeMemory = freeMemory
        return worker.build()
    }
}
