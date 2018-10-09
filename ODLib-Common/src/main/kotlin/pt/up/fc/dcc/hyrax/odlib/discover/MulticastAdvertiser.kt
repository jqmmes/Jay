package pt.up.fc.dcc.hyrax.odlib.discover

import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.ODLogger
import java.lang.Thread.sleep
import java.net.*
import kotlin.concurrent.thread

class MulticastAdvertiser {
    companion object {
        private var running = false
        private lateinit var mcSocket : MulticastSocket
        private lateinit var mcIPAddress : InetAddress
        var multicastFrequency = 1000L

        fun advertise(networkInterface: NetworkInterface? = null) {
            if (running) {
                ODLogger.logWarn("MulticastServer already running")
                return
            }

            thread(isDaemon=true) {
                val mcPort = 50000
                val mcIPStr = "224.0.0.1"
                running = true
                mcSocket = MulticastSocket()
                if (networkInterface != null) {
                    mcSocket.networkInterface = networkInterface
                } else {
                    val interfaces = NetworkUtils.getCompatibleInterfaces<Inet4Address>()
                    if (!interfaces.isEmpty()) {
                        ODLogger.logInfo("Using default interface (${interfaces[0]}) to advertise")
                        mcSocket.networkInterface = interfaces[0]
                    } else {
                        ODLogger.logWarn("Not suitable Multicast interface found")
                        return@thread
                    }
                }
                mcSocket.loopbackMode = true
                val msg = ByteArray(1)
                mcIPAddress = Inet4Address.getByName(mcIPStr)
                mcSocket.joinGroup(mcIPAddress)
                val packet = DatagramPacket(msg, 1, mcIPAddress, mcPort)

                do {
                    ODLogger.logInfo("Sending Multicast packet")
                    mcSocket.send(packet)
                    sleep(multicastFrequency)
                } while (running)

                mcSocket.leaveGroup(mcIPAddress)
                mcSocket.close()
            }
        }

        fun stop() {
            running = false
        }
    }
}