package pt.up.fc.dcc.hyrax.jay.services.broker.multicast

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
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
                    JayLogger.logInfo("USING_DEFAULT_INTERFACE", actions = *arrayOf("LISTEN_INTERFACE=${interfaces[0]}"))
                    listeningSocket.networkInterface = interfaces[0]
                } else {
                    JayLogger.logError("NO_SUITABLE_INTERFACE_FOUND")
                    return@thread
                }
            }
            JayLogger.logInfo("RECEIVER", actions = *arrayOf("RUNNING_AT=${listeningSocket.localSocketAddress}"))
            listeningSocket.joinGroup(mcIPAddress)

            running = true
            var packet : DatagramPacket?
            do {
                packet = DatagramPacket(ByteArray(1024), 1024)

                try {
                    listeningSocket.receive(packet)
                } catch (e: SocketException) {
                    JayLogger.logWarn("CLOSE", actions = *arrayOf("ERROR=SOCKET_EXCEPTION"))
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
