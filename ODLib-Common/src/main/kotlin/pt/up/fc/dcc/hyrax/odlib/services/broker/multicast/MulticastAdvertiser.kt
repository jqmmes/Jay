package pt.up.fc.dcc.hyrax.odlib.services.broker.multicast

import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.lang.Thread.sleep
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object MulticastAdvertiser {
    private lateinit var mcSocket : MulticastSocket
    private var multicastFrequency = 1000L
    private val MESSAGE_LOCK = Object()
    private const val mcPort = 50000
    private const val mcIPStr = "224.0.0.1"
    private var mcIPAddress : InetAddress = Inet4Address.getByName(mcIPStr)
    private var packet: DatagramPacket? = null
    private val runningLock = AtomicBoolean(false)

    @Suppress("unused")
    fun setFrequency(frequency: Long) {
        this.multicastFrequency = frequency
    }

    fun setAdvertiseData(data: ByteArray?) {
        val msg = data ?: ByteArray(0)
        synchronized(MESSAGE_LOCK) {
            packet = DatagramPacket(msg, msg.size, mcIPAddress, mcPort)
        }
    }

    fun start(data: ByteArray?, networkInterface: NetworkInterface? = null) {
        val msg = data ?: ByteArray(0)
        setAdvertiseData(msg)
        start(networkInterface)
    }

    fun start(networkInterface: NetworkInterface? = null) {
        if (runningLock.get()) {
            ODLogger.logWarn("ALREADY_RUNNING")
            return
        }

        thread(isDaemon=true, name="Multicast Advertiser") {
            ODLogger.logInfo("INIT_THREAD")
            if(runningLock.getAndSet(true)) return@thread
            mcSocket = MulticastSocket()
            if (networkInterface != null) {
                mcSocket.networkInterface = networkInterface
            } else {
                val interfaces = ODUtils.getCompatibleInterfaces<Inet4Address>()
                if (!interfaces.isEmpty()) {
                    ODLogger.logInfo("USING_DEFAULT_INTERFACE",  actions = *arrayOf("ADVERTISE_INTERFACE=${interfaces[0]}"))
                    mcSocket.networkInterface = interfaces[0]
                } else {
                    ODLogger.logWarn("NO_SUITABLE_INTERFACE_FOUND")
                    return@thread
                }
            }
            mcSocket.loopbackMode = true
            mcSocket.joinGroup(mcIPAddress)
            do {
                synchronized(MESSAGE_LOCK) { if (packet != null)
                    try {
                        mcSocket.send(packet)
                        ODLogger.logInfo("SENT_MULTICAST_PACKET", actions = *arrayOf("INTERFACE=${mcSocket.`interface`.address}", "PACKET_SIZE=${packet?.data?.size}"))
                    } catch(e: SocketException) {
                        ODLogger.logError("MULTICAST_SOCKET_ERROR", actions = *arrayOf("INTERFACE=${mcSocket.`interface`.address}", "PACKET_SIZE=${packet?.data?.size}"))
                    }
                }
                sleep(multicastFrequency)
            } while (runningLock.get())

            if (!mcSocket.isClosed) {
                mcSocket.leaveGroup(mcIPAddress)
                mcSocket.close()
            }
        }
    }



    internal fun isRunning() : Boolean {
        return runningLock.get()
    }

    fun stop() {
        ODLogger.logInfo("INIT")
        runningLock.set(false)
        packet = null
        ODLogger.logInfo("COMPLETE")
    }

}