package pt.up.fc.dcc.hyrax.odlib.multicast

import pt.up.fc.dcc.hyrax.odlib.utils.NetworkUtils
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.lang.Thread.sleep
import java.net.*
import kotlin.concurrent.thread

class MulticastAdvertiser {
    companion object {
        private var running = false
        private lateinit var mcSocket : MulticastSocket
        var multicastFrequency = 1000L
        private var advertisingData = ByteArray(1)
        private val MESSAGE_LOCK = Object()
        private const val mcPort = 50000
        private const val mcIPStr = "224.0.0.1"
        private var mcIPAddress : InetAddress = Inet4Address.getByName(mcIPStr)
        private var packet = DatagramPacket(advertisingData, 1, mcIPAddress, mcPort)

        fun setAdvertiseData(data: ByteArray) {
            synchronized(MESSAGE_LOCK) {
                packet = DatagramPacket(data, data.size, mcIPAddress, mcPort)
            }
        }

        fun advertise(networkInterface: NetworkInterface? = null) {
            if (running) {
                ODLogger.logWarn("MulticastServer already running")
                return
            }

            thread(isDaemon=true) {
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
                mcSocket.joinGroup(mcIPAddress)
                do {
                    ODLogger.logInfo("Sending Multicast packet")
                    synchronized(MESSAGE_LOCK) { mcSocket.send(packet) }
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