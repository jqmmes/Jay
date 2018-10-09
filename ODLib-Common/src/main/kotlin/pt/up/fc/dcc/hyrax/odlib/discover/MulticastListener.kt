package pt.up.fc.dcc.hyrax.odlib.discover

import pt.up.fc.dcc.hyrax.odlib.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.discover.NetworkUtils.Companion.getHostAddressFromPacket
import pt.up.fc.dcc.hyrax.odlib.interfaces.DiscoverInterface
import pt.up.fc.dcc.hyrax.odlib.ODLogger
import java.net.*
import kotlin.concurrent.thread

class MulticastListener {
    companion object {
        private val devicesKnown : MutableSet<RemoteODClient> = HashSet()
        private var running = false
        private lateinit var listeningSocket : MulticastSocket
        private lateinit var mcIPAddress: InetAddress

        fun listen(callback : DiscoverInterface, networkInterface: NetworkInterface? = null) {
            if (running) {
                ODLogger.logInfo("Multicast MulticastListener already running")
                return
            }
            thread(isDaemon = true) {
                val mcPort = 50000
                val mcIPStr = "224.0.0.1"
                mcIPAddress = Inet4Address.getByName(mcIPStr)
                listeningSocket = MulticastSocket(mcPort)
                if (networkInterface != null) {
                    listeningSocket.networkInterface = networkInterface
                } else {
                    val interfaces = NetworkUtils.getCompatibleInterfaces<Inet4Address>()
                    if (!interfaces.isEmpty()) {
                        ODLogger.logInfo("Using default interface (${interfaces[0]}) to advertise")
                        listeningSocket.networkInterface = interfaces[0]
                    } else {
                        ODLogger.logWarn("Not suitable Multicast interface found")
                        return@thread
                    }
                }
                ODLogger.logInfo("Multicast Receiver running at:" + listeningSocket.localSocketAddress)
                listeningSocket.joinGroup(mcIPAddress)

                running = true
                var packet : DatagramPacket?
                do {
                    packet = DatagramPacket(ByteArray(1024), 1024)

                    ODLogger.logInfo("Waiting for a  multicast message...")
                    try {
                        listeningSocket.receive(packet)
                    }catch (e: SocketException) {
                        ODLogger.logWarn("Socket error")
                        running = false
                        continue
                    }
                    if (newClient(packet.address.hostAddress)) {
                        callback.onMulticastReceived(packet) // getHostAddressFromPacket(packet)
                        ODLogger.logInfo("Packet received from ${getHostAddressFromPacket(packet)}")
                    }
                } while (running)
                if (!listeningSocket.isClosed) {
                    listeningSocket.leaveGroup(mcIPAddress)
                    listeningSocket.close()
                }
            }
        }

        fun stop() {
            running = false
            listeningSocket.leaveGroup(mcIPAddress)
            listeningSocket.close()
        }

        private fun newClient(address: String): Boolean {
            for (remoteDevice in devicesKnown) {
                if (remoteDevice.getAddress() == address) return false
            }
            return true
        }
    }
}
