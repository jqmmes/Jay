package pt.up.fc.dcc.hyrax.odlib.services.broker.multicast

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.utils.NetworkUtils
import pt.up.fc.dcc.hyrax.odlib.utils.NetworkUtils.getHostAddressFromPacket
import pt.up.fc.dcc.hyrax.odlib.utils.NetworkUtils.getLocalIpV4
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.net.*
import kotlin.concurrent.thread

object MulticastListener {
    //private val devicesKnown : MutableSet<RemoteODClient> = HashSet()
    private var running = false
    private lateinit var listeningSocket : MulticastSocket
    private lateinit var mcIPAddress: InetAddress

    fun listen(callback: ((ODProto.Worker?, String) -> Unit)? = null, networkInterface: NetworkInterface? = null) {
        if (running) {
            ODLogger.logInfo("Multicast MulticastListener already running")
            return
        }
        thread(isDaemon = true, name="Multicast Listener") {
            val localIp = getLocalIpV4()
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

                //ODLogger.logInfo("Waiting for a  multicast message...")
                try {
                    listeningSocket.receive(packet)
                } catch (e: SocketException) {
                    ODLogger.logWarn("Socket error")
                    running = false
                    continue
                }
                if (listeningSocket.`interface`.isLoopbackAddress || getHostAddressFromPacket(packet) != localIp) {
                    //ODLogger.logInfo("Packet received from ${getHostAddressFromPacket(packet)}")
                    //DatagramProcessor.process(packet)
                    try {
                        callback?.invoke(ODProto.Worker.parseFrom(packet.data), getHostAddressFromPacket(packet))
                    } catch (ignore: Exception) {

                    }
                }
                /*if (newClient(packet.address.hostAddress)) {
                    callback.onNewClient(packet) // getHostAddressFromPacket(packet)
                    ODLogger.logInfo("Packet received from ${getHostAddressFromPacket(packet)}")
                }*/
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
}
