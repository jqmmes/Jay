package pt.up.fc.dcc.hyrax.odlib.discover

import pt.up.fc.dcc.hyrax.odlib.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.interfaces.DiscoverInterface
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODLib
import java.net.*
import kotlin.concurrent.thread

class MulticastListener {
    companion object {
        private val devicesKnown : MutableSet<RemoteODClient> = HashSet()
        private var running = false
        private lateinit var listeningSocket : MulticastSocket
        private lateinit var mcIPAddress: InetAddress

        fun listen(callback : DiscoverInterface) {
            if (running) {
                ODLib.log("Multicast MulticastListener already running")
                return
            }
            thread(isDaemon = true) {
                val mcPort = 50000
                //val mcIPStr = "224.0.1.0"
                //ffxe::/16
                val mcIPStr = "FF7E:230::1234"
                //mcIPAddress = Inet4Address.getByName(mcIPStr)
                mcIPAddress = Inet6Address.getByName(mcIPStr)
                listeningSocket = MulticastSocket(mcPort)
                ODLib.log("Multicast Receiver running at:" + listeningSocket.localSocketAddress)
                println("Multicast Receiver running at:" + listeningSocket.localSocketAddress)
                listeningSocket.joinGroup(mcIPAddress!!)

                running = true
                var packet : DatagramPacket?
                do {
                    packet = DatagramPacket(ByteArray(1024), 1024)

                    ODLib.log("Waiting for a  multicast message...")
                    try {
                        listeningSocket.receive(packet)
                    }catch (e: SocketException) {
                        ODLib.log("Socket error")
                        running = false
                        continue
                    }
                    if (newClient(packet.address.hostAddress)) {
                        callback.onNewClientFound(RemoteODClient(packet.address.hostAddress.substringBefore("%"), 50001))
                        ODLib.log("Client found ${packet.address.address}")
                    }
                    /*val msg = String(packet.data, packet.offset, packet.length)*/
                    //println("[Multicast  Receiver] Received:$msg")
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
                if (remoteDevice.getAdress() == address) return false
            }
            return true
        }
    }
}
