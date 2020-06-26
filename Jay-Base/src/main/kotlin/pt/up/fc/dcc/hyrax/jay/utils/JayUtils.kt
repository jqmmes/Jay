package pt.up.fc.dcc.hyrax.jay.utils

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Worker.Type
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayState
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
                        if (JaySettings.MULTICAST_INTERFACE == null || (JaySettings.MULTICAST_INTERFACE != null && netInt.name ==
                                        JaySettings.MULTICAST_INTERFACE)) {
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

    fun getTaskDetails(task: JayProto.Task?): JayProto.TaskDetails? {
        if (task == null) return JayProto.TaskDetails.getDefaultInstance()
        return JayProto.TaskDetails.newBuilder().setId(task.id).setDataSize(task.data.size()).build()
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

    fun genStatus(bool: Boolean): JayProto.Status? {
        return when (bool) {
            true -> genStatusSuccess()
            else -> genStatusError()
        }
    }

    fun genJayState(stateProto: JayProto.JayState?): JayState? {
        if (stateProto == null)
            return null
        return when (stateProto.jayState) {
            JayProto.JayState.state.IDLE -> JayState.IDLE
            JayProto.JayState.state.DATA_RCV -> JayState.DATA_RCV
            JayProto.JayState.state.DATA_SND -> JayState.DATA_SND
            JayProto.JayState.state.COMPUTE -> JayState.COMPUTE
            JayProto.JayState.state.MULTICAST_ADVERTISE -> JayState.MULTICAST_ADVERTISE
            JayProto.JayState.state.MULTICAST_LISTEN -> JayState.MULTICAST_LISTEN
            else -> null
        }
    }

    fun genJayStateProto(state: JayState?): JayProto.JayState? {
        if (state == null)
            return null
        val jayState = when (state) {
            JayState.IDLE -> JayProto.JayState.state.IDLE
            JayState.DATA_RCV -> JayProto.JayState.state.DATA_RCV
            JayState.DATA_SND -> JayProto.JayState.state.DATA_SND
            JayState.COMPUTE -> JayProto.JayState.state.COMPUTE
            JayState.MULTICAST_ADVERTISE -> JayProto.JayState.state.MULTICAST_ADVERTISE
            JayState.MULTICAST_LISTEN -> JayProto.JayState.state.MULTICAST_LISTEN
        }
        return JayProto.JayState.newBuilder().setJayState(jayState).build()
    }

    fun genWorkerProto(id: String? = null, batteryLevel: Int, batteryCurrent: Int, batteryVoltage: Int,
                       batteryTemperature: Float, batteryEnergy: Long, batteryCharge: Int,
                       avgComputingEstimate: Long, cpuCores: Int, queueSize: Int,
                       queuedTasks: Int, runningTasks: Int, type: Type? = null,
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
        worker.avgTimePerTask = avgComputingEstimate // Modified by Worker
        worker.cpuCores = cpuCores // Set by Worker
        worker.queueSize = queueSize // Set by Worker
        worker.queuedTasks = queuedTasks
        worker.runningTasks = runningTasks // Modified by Worker
        if (type != null) worker.type = type // Set in Broker
        if (bandwidthEstimate != null) worker.bandwidthEstimate = bandwidthEstimate // Set internally
        worker.totalMemory = totalMemory
        worker.freeMemory = freeMemory
        return worker.build()
    }
}
