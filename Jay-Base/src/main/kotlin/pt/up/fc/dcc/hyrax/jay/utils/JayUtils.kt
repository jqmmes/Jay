package pt.up.fc.dcc.hyrax.jay.utils

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Worker.Type
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
                for (address in netInt.inetAddresses) {
                    if (address is T) {
                        if (JaySettings.MCAST_INTERFACE == null || (JaySettings.MCAST_INTERFACE != null && netInt.name ==
                                        JaySettings.MCAST_INTERFACE)) {
                            JayLogger.logInfo("INTERFACE_AVAILABLE", actions = *arrayOf("INTERFACE=${netInt.name}"))
                            interfaceList.add(netInt)
                        }
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

    internal fun genResponse(id: String, results: ByteString): JayProto.Response {
        val builder = JayProto.Response.newBuilder()
                .setId(id)
        builder.bytes = results
        return builder.build()
    }

    fun genStatus(code: JayProto.StatusCode): JayProto.Status? {
        return JayProto.Status.newBuilder().setCode(code).build()
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
