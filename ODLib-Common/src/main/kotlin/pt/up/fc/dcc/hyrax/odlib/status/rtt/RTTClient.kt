package pt.up.fc.dcc.hyrax.odlib.status.rtt

import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.lang.Exception
import java.net.Socket
import kotlin.concurrent.thread

object RTTClient {
    private lateinit var clientSocket : Socket

    fun measureRTT(odClient: RemoteODClient, rttPort: Int,  numPackets: Int = 1000, useUdp: Boolean = false) {
        thread(isDaemon = true, name="RTTClient") {
            if (useUdp) return@thread
            try { clientSocket = Socket(odClient.getAddress(), rttPort) } catch (e: Exception) {return@thread}
            ODLogger.logInfo("RTTClient measuring rtt to ${odClient.getAddress()}")
            clientSocket.tcpNoDelay = true
            var total = 0L
            var lastPkt = 1
            for (x in 1..numPackets) {
                try {
                    val start = System.currentTimeMillis()
                    clientSocket.getOutputStream().write(ByteArray(1))
                    clientSocket.getInputStream().read()
                    val now = System.currentTimeMillis()
                    total += (now.minus(start))
                    lastPkt = x
                } catch (e: Exception) {}
            }
            if (lastPkt >= numPackets/2 || lastPkt >= 10) {
                odClient.getDeviceInformation().rtt = total.toFloat() / lastPkt.toFloat()
                ODLogger.logInfo("RTTClient: RTT for ${odClient.getAddress()} is ${odClient.getDeviceInformation().rtt}")
            }
            try { clientSocket.close() } catch (e: Exception) {}
        }
    }
}