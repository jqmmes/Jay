package pt.up.fc.dcc.hyrax.odlib.multicast

import pt.up.fc.dcc.hyrax.odlib.utils.NetworkUtils
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.lang.Thread.sleep
import java.net.*
import kotlin.concurrent.thread

class MulticastAdvertiser {
    companion object {
        private var running = false
        private lateinit var mcSocket : MulticastSocket
        var multicastFrequency = 1000L
        private val MESSAGE_LOCK = Object()
        private const val mcPort = 50000
        private const val mcIPStr = "224.0.0.1"
        private var mcIPAddress : InetAddress = Inet4Address.getByName(mcIPStr)
        private var packet: DatagramPacket? = null
        private var currentAdvertiseType = 0

        init { setAdvertiseData() }

        fun setAdvertiseData(msgType: Int = 0, data: ByteArray = ByteArray(0)) {
            synchronized(MESSAGE_LOCK) {
                val baos = ByteArrayOutputStream()
                ObjectOutputStream(baos).writeObject(AdvertisingMessage(msgType, data))
                packet = DatagramPacket(baos.toByteArray(), baos.size(), mcIPAddress, mcPort)
                currentAdvertiseType = msgType
            }
        }

        fun getCurrentAdvertiseType(): Int {
            return currentAdvertiseType
        }

        fun advertise(networkInterface: NetworkInterface? = null) {
            if (running) {
                ODLogger.logWarn("MulticastServer already running")
                return
            }

            thread(isDaemon=true, name="Multicast Advertiser") {
                ODLogger.logInfo("Starting Multicast Advertiser")
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
                    //ODLogger.logInfo("Sending Multicast packet")
                    synchronized(MESSAGE_LOCK) { mcSocket.send(packet) }
                    sleep(multicastFrequency)
                } while (running)

                if (!mcSocket.isClosed) {
                    mcSocket.leaveGroup(mcIPAddress)
                    mcSocket.close()
                }
            }
        }

        fun stop() {
            running = false
        }
    }
}