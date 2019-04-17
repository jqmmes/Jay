package pt.up.fc.dcc.hyrax.odlib.services.broker.multicast

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils.getHostAddressFromPacket
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils.getLocalIpV4
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.net.*
import kotlin.concurrent.thread

object MulticastListener {
    private var running = false
    private lateinit var listeningSocket : MulticastSocket
    private lateinit var mcIPAddress: InetAddress

    fun listen(callback: ((ODProto.Worker?, String) -> Unit)? = null, networkInterface: NetworkInterface? = null) {
        if (running) {
            ODLogger.logWarn("MulticastListener, ALREADY_RUNNING")
            return
        }
        ODLogger.logInfo("MulticastListener, LISTEN, INIT")
        thread(isDaemon = true, name="Multicast Listener") {
            val localIp = getLocalIpV4()
            val mcPort = 50000
            val mcIPStr = "224.0.0.1"
            mcIPAddress = Inet4Address.getByName(mcIPStr)
            listeningSocket = MulticastSocket(mcPort)
            if (networkInterface != null) {
                listeningSocket.networkInterface = networkInterface
            } else {
                val interfaces = ODUtils.getCompatibleInterfaces<Inet4Address>()
                if (!interfaces.isEmpty()) {
                    ODLogger.logInfo("MulticastListener, LISTEN, USING_DEFAULT_INTERFACE, ADVERTISE_INTERFACE=${interfaces[0]}")
                    listeningSocket.networkInterface = interfaces[0]
                } else {
                    ODLogger.logError("MulticastListener, LISTEN, NO_SUITABLE_INTERFACE_FOUND")
                    return@thread
                }
            }
            ODLogger.logInfo("MulticastListener, LISTEN, RECEIVER, RUNNING_AT=${listeningSocket.localSocketAddress}")
            listeningSocket.joinGroup(mcIPAddress)

            running = true
            var packet : DatagramPacket?
            do {
                packet = DatagramPacket(ByteArray(1024), 1024)

                try {
                    listeningSocket.receive(packet)
                } catch (e: SocketException) {
                    ODLogger.logWarn("MulticastListener, CLOSE, SOCKET_EXCEPTION")
                    running = false
                    continue
                }
                if (listeningSocket.`interface`.isLoopbackAddress || getHostAddressFromPacket(packet) != localIp) {
                    try {
                        callback?.invoke(ODProto.Worker.parseFrom(ByteArray(packet.length) { pos -> packet.data[pos] }), getHostAddressFromPacket(packet))
                    } catch (ignore: Exception) { }
                }
            } while (running)
            if (!listeningSocket.isClosed) {
                ODLogger.logInfo("MulticastListener, SOCKET_CLOSED")
                listeningSocket.leaveGroup(mcIPAddress)
                listeningSocket.close()
            }
        }
    }

    fun stop() {
        ODLogger.logInfo("MulticastListener, STOP")
        running = false
        try {
            listeningSocket.leaveGroup(mcIPAddress)
            listeningSocket.close()
        } catch (ignore: Exception) {}
    }
}
