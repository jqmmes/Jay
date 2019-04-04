package pt.up.fc.dcc.hyrax.odlib.utils

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.structures.ODDetection
import pt.up.fc.dcc.hyrax.odlib.structures.ODModel
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
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
                        ODLogger.logInfo("Available Multicast interface: ${netInt.name}")
                        interfaceList.add(netInt)
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

    fun genModelRequest(listModels: Set<ODModel>): ODProto.Models? {
        val models = ODProto.Models.newBuilder()
        for (model in listModels) {
            models.addModels(model.getProto())
        }
        return models.build()
    }

    fun genStatus(code: ODProto.StatusCode): ODProto.Status? {
        return ODProto.Status.newBuilder().setCode(code).build()
    }

    fun parseModels(models: ODProto.Models?): Set<ODModel> {
        val modelSet : MutableSet<ODModel> = mutableSetOf()
        for (model in models!!.modelsList) {
            modelSet.add(ODModel(model))
        }
        return modelSet.toSet()
    }

    fun parseSchedulers(schedulers: ODProto.Schedulers?): Set<Pair<String, String>> {
        val schedulerSet : MutableSet<Pair<String, String>> = mutableSetOf()
        for (scheduler in schedulers!!.schedulerList) schedulerSet.add(Pair(scheduler.id, scheduler.name))
        return schedulerSet
    }

    fun genWorkerTypes(vararg types: ODProto.Worker.Type): ODProto.WorkerTypes {
        return ODProto.WorkerTypes.newBuilder().addAllType(types.asIterable()).build()
    }

    fun genWorkerTypes(types: List<ODProto.Worker.Type>): ODProto.WorkerTypes {
        return ODProto.WorkerTypes.newBuilder().addAllType(types).build()
    }
}
