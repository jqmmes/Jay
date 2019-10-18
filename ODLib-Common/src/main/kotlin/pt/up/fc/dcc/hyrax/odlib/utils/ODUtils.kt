package pt.up.fc.dcc.hyrax.odlib.utils

import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.structures.Detection
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.NetworkInterface

object ODUtils {

    private var localIPv4: String = ""
    private var firstRefresh: Boolean = true

    internal inline fun <reified T>getCompatibleInterfaces(): List<NetworkInterface> {
        val interfaceList : MutableList<NetworkInterface> = mutableListOf()
        for (netInt in NetworkInterface.getNetworkInterfaces()) {
            if (!netInt.isLoopback && !netInt.isPointToPoint && netInt.isUp && netInt.supportsMulticast()) {
                for (address in netInt.inetAddresses)
                    if (address is T) {
                        if (ODSettings.MCAST_INTERFACE == null || (ODSettings.MCAST_INTERFACE != null && netInt.name ==
                                        ODSettings.MCAST_INTERFACE)) {
                            ODLogger.logInfo("INTERFACE_AVAILABLE", actions = *arrayOf("INTERFACE=${netInt.name}"))
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
            if (!interfaces.isEmpty()) {
                for (ip in interfaces[0].inetAddresses) {
                    if (ip is Inet4Address) localIPv4 = ip.toString().trim('/')
                }
            }
        }
        return localIPv4
    }

    fun getHostAddressFromPacket(packet: DatagramPacket) : String {
        return packet.address.hostAddress.substringBefore("%")
    }

    private fun genDetection(detection: Detection?) : ODProto.Detection{
        return ODProto.Detection.newBuilder()
                .setClass_(detection!!.class_)
                .setScore(detection.score)
                .build()
    }

    internal fun genResults(id: String, results: List<Detection?>) : ODProto.Results {
        val builder = ODProto.Results.newBuilder()
                .setId(id)
        for (detection in results) {
            builder.addDetections(genDetection(detection))
        }
        return builder.build()
    }

    fun genModelRequest(listModels: Set<Model>): ODProto.Models? {
        val models = ODProto.Models.newBuilder()
        for (model in listModels) {
            models.addModels(model.getProto())
        }
        return models.build()
    }

    fun genStatus(code: ODProto.StatusCode): ODProto.Status? {
        return ODProto.Status.newBuilder().setCode(code).build()
    }

    fun parseModels(models: ODProto.Models?): Set<Model> {
        val modelSet : MutableSet<Model> = mutableSetOf()
        for (model in models!!.modelsList) {
            modelSet.add(Model(model))
        }
        return modelSet.toSet()
    }

    fun parseSchedulers(schedulers: ODProto.Schedulers?): Set<Pair<String, String>> {
        val schedulerSet : MutableSet<Pair<String, String>> = mutableSetOf()
        for (scheduler in schedulers!!.schedulerList) schedulerSet.add(Pair(scheduler.id, scheduler.name))
        return schedulerSet
    }

    fun getJobDetails(job: ODProto.Job?) : ODProto.JobDetails? {
        if (job == null) return ODProto.JobDetails.getDefaultInstance()
        return ODProto.JobDetails.newBuilder().setId(job.id).setDataSize(job.data.size()).build()
    }

    fun genWorkerTypes(vararg types: ODProto.Worker.Type): ODProto.WorkerTypes {
        return ODProto.WorkerTypes.newBuilder().addAllType(types.asIterable()).build()
    }

    fun genWorkerTypes(types: List<ODProto.Worker.Type>): ODProto.WorkerTypes {
        return ODProto.WorkerTypes.newBuilder().addAllType(types).build()
    }

    fun genStatusSuccess(): ODProto.Status? { return genStatus(ODProto.StatusCode.Success) }
    fun genStatusError(): ODProto.Status? { return genStatus(ODProto.StatusCode.Success) }
}
