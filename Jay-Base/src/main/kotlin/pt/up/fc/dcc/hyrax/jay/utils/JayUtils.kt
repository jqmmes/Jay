/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

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
                            JayLogger.logInfo("INTERFACE_AVAILABLE", actions = arrayOf("INTERFACE=${netInt.name}"))
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

    fun genStatus(code: JayProto.StatusCode): JayProto.Status {
        return JayProto.Status.newBuilder().setCode(code).build()
    }

    fun parseSchedulers(schedulers: JayProto.Schedulers?): Set<Pair<String, String>> {
        val schedulerSet: MutableSet<Pair<String, String>> = mutableSetOf()
        for (scheduler in schedulers!!.schedulerList) schedulerSet.add(Pair(scheduler.id, scheduler.name))
        return schedulerSet
    }

    fun genWorkerTypes(vararg types: Type): JayProto.WorkerTypes {
        return JayProto.WorkerTypes.newBuilder().addAllType(types.asIterable()).build()
    }

    fun genWorkerTypes(types: List<Type>): JayProto.WorkerTypes {
        return JayProto.WorkerTypes.newBuilder().addAllType(types).build()
    }

    fun genStatusSuccess(): JayProto.Status {
        return genStatus(JayProto.StatusCode.Success)
    }

    fun genStatusError(): JayProto.Status {
        return genStatus(JayProto.StatusCode.Success)
    }

    fun genStatus(bool: Boolean): JayProto.Status {
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
}
