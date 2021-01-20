/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.services.broker.multicast

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.jay.JayState
import pt.up.fc.dcc.hyrax.jay.utils.JaySettings
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.io.IOException
import java.lang.Thread.sleep
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object MulticastAdvertiser {
    private lateinit var mcSocket : MulticastSocket
    private val MESSAGE_LOCK = Object()
    private const val mcPort = 50000
    private const val mcIPStr = "224.0.0.1"
    private var mcIPAddress : InetAddress = Inet4Address.getByName(mcIPStr)
    private var packet: DatagramPacket? = null
    private val runningLock = AtomicBoolean(false)

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

    private fun start(networkInterface: NetworkInterface? = null) {
        if (runningLock.get()) {
            JayLogger.logWarn("ALREADY_RUNNING")
            return
        }
        thread(isDaemon=true, name="Multicast Advertiser") {
            JayLogger.logInfo("INIT_THREAD")
            if(runningLock.getAndSet(true)) return@thread
            BrokerService.profiler.setState(JayState.MULTICAST_ADVERTISE)
            mcSocket = MulticastSocket()
            if (networkInterface != null) {
                mcSocket.networkInterface = networkInterface
            } else {
                val interfaces = JayUtils.getCompatibleInterfaces<Inet4Address>()
                if (interfaces.isNotEmpty()) {
                    JayLogger.logInfo("USING_DEFAULT_INTERFACE", actions = arrayOf("ADVERTISE_INTERFACE=${interfaces[0]}"))
                    mcSocket.networkInterface = interfaces[0]
                } else {
                    JayLogger.logWarn("NO_SUITABLE_INTERFACE_FOUND")
                    return@thread
                }
            }
            mcSocket.loopbackMode = true
            mcSocket.joinGroup(mcIPAddress)
            do {
                synchronized(MESSAGE_LOCK) {
                    if (packet != null && JaySettings.ADVERTISE_WORKER_STATUS)
                        try {
                            mcSocket.send(packet)
                            JayLogger.logInfo("SENT_MULTICAST_PACKET", actions = arrayOf("INTERFACE=${mcSocket.`interface`.address}", "PACKET_SIZE=${packet?.data?.size}"))
                        } catch (e: SocketException) {
                            JayLogger.logError("MULTICAST_SOCKET_ERROR", actions = arrayOf("ERROR=SocketException", "INTERFACE=${mcSocket.`interface`.address}", "PACKET_SIZE=${packet?.data?.size}"))
                        } catch (e: IOException) {
                            JayLogger.logError("MULTICAST_SOCKET_ERROR", actions = arrayOf("ERROR=IOException", "INTERFACE=${mcSocket.`interface`.address}", "PACKET_SIZE=${packet?.data?.size}"))
                        }
                }
                sleep(JaySettings.MULTICAST_PKT_INTERVAL)
            } while (runningLock.get())

            if (!mcSocket.isClosed) {
                mcSocket.leaveGroup(mcIPAddress)
                mcSocket.close()
            }
            BrokerService.profiler.unSetState(JayState.MULTICAST_ADVERTISE)
        }
    }



    internal fun isRunning() : Boolean {
        return runningLock.get()
    }

    fun stop() {
        JayLogger.logInfo("INIT")
        runningLock.set(false)
        packet = null
        JayLogger.logInfo("COMPLETE")
    }

}