package pt.up.fc.dcc.hyrax.odlib.services.broker.multicast

import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
/*import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream*/
import java.lang.Thread.sleep
import java.net.*
import kotlin.concurrent.thread

object MulticastAdvertiser {
    private var running = false
    private lateinit var mcSocket : MulticastSocket
    private var multicastFrequency = 1000L
    private val MESSAGE_LOCK = Object()
    private const val mcPort = 50000
    private const val mcIPStr = "224.0.0.1"
    private var mcIPAddress : InetAddress = Inet4Address.getByName(mcIPStr)
    private var packet: DatagramPacket? = null
    private var currentAdvertiseType = 0

    @Suppress("unused")
    fun setFrequency(frequency: Long) {
        this.multicastFrequency = frequency
    }

    fun setAdvertiseData(data: ByteArray) {
        synchronized(MESSAGE_LOCK) {
            packet = DatagramPacket(data, data.size, mcIPAddress, mcPort)
        }
    }


    fun getCurrentAdvertiseType(): Int {
        return currentAdvertiseType
    }

    fun start(data: ByteArray, networkInterface: NetworkInterface? = null) {
        setAdvertiseData(data)
        start(networkInterface)
    }

    fun start(networkInterface: NetworkInterface? = null) {
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
                val interfaces = ODUtils.getCompatibleInterfaces<Inet4Address>()
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
                synchronized(MESSAGE_LOCK) { if (packet != null) mcSocket.send(packet) }
                sleep(multicastFrequency)
            } while (running)

            if (!mcSocket.isClosed) {
                mcSocket.leaveGroup(mcIPAddress)
                mcSocket.close()
            }
        }
    }

    internal fun isRunning() : Boolean {
        return running
    }

    fun stop() {
        running = false
        packet = null
    }

}