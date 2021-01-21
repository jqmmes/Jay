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

package pt.up.fc.dcc.hyrax.jay.services.broker.multicast

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayState
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils.getHostAddressFromPacket
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils.getLocalIpV4
import java.net.*
import kotlin.concurrent.thread

object MulticastListener {
    private var running = false
    private lateinit var listeningSocket : MulticastSocket
    private lateinit var mcIPAddress: InetAddress
    private var isStopping = false

    fun listen(callback: ((JayProto.Worker?, String) -> Unit)? = null, networkInterface: NetworkInterface? = null) {
        if (running) {
            JayLogger.logWarn("ALREADY_RUNNING")
            return
        }
        JayLogger.logInfo("INIT")
        thread(isDaemon = true, name = "Multicast Listener") {
            val localIp = getLocalIpV4()
            val mcPort = 50000
            val mcIPStr = "224.0.0.1"
            mcIPAddress = Inet4Address.getByName(mcIPStr)
            listeningSocket = MulticastSocket(mcPort)
            if (networkInterface != null) {
                listeningSocket.networkInterface = networkInterface
            } else {
                val interfaces = JayUtils.getCompatibleInterfaces<Inet4Address>()
                if (interfaces.isNotEmpty()) {
                    JayLogger.logInfo("USING_DEFAULT_INTERFACE", actions = arrayOf("LISTEN_INTERFACE=${interfaces[0]}"))
                    listeningSocket.networkInterface = interfaces[0]
                } else {
                    JayLogger.logError("NO_SUITABLE_INTERFACE_FOUND")
                    return@thread
                }
            }
            JayLogger.logInfo("RECEIVER", actions = arrayOf("RUNNING_AT=${listeningSocket.localSocketAddress}"))
            listeningSocket.joinGroup(mcIPAddress)
            BrokerService.profiler.setState(JayState.MULTICAST_LISTEN)
            running = true
            var packet : DatagramPacket?
            do {
                packet = DatagramPacket(ByteArray(1024), 1024)

                try {
                    listeningSocket.receive(packet)
                } catch (e: SocketException) {
                    JayLogger.logWarn("CLOSE", actions = arrayOf("ERROR=SOCKET_EXCEPTION"))
                    running = false
                    continue
                }
                if (listeningSocket.`interface`.isLoopbackAddress || getHostAddressFromPacket(packet) != localIp) {
                    try {
                        callback?.invoke(JayProto.Worker.parseFrom(ByteArray(packet.length) { pos -> packet.data[pos] }), getHostAddressFromPacket(packet))
                    } catch (ignore: Exception) { }
                }
            } while (running)
            if (!listeningSocket.isClosed) {
                if (isStopping) {
                    JayLogger.logInfo("SOCKET_CLOSED")
                    isStopping = false
                }
                listeningSocket.leaveGroup(mcIPAddress)
                listeningSocket.close()
            }
            BrokerService.profiler.unSetState(JayState.MULTICAST_LISTEN)
        }
    }

    fun stop() {
        JayLogger.logInfo("INIT")
        running = false
        isStopping = true
        try {
            listeningSocket.leaveGroup(mcIPAddress)
            listeningSocket.close()
        } catch (ignore: Exception) {}
        isStopping = true
        JayLogger.logInfo("COMPLETE")
    }
}
