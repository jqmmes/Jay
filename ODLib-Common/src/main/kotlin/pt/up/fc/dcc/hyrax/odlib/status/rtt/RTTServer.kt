package pt.up.fc.dcc.hyrax.odlib.status.rtt

import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

object RTTServer {
    private lateinit var serverSocket : ServerSocket

    private val running = HashMap<Int, Boolean>()
    private val acceptingSockets = HashMap<Int, MutableSet<Socket>>()

    fun startServer(port: Int, numPackets: Int = 1000, useUdp: Boolean = false) {
        thread(isDaemon = true, name = "RTTServer Daemon ${if (useUdp) "UDP" else "TCP"} port: $port") {
            ODLogger.logInfo("Starting RTTServer on port $port")
            if (useUdp || (running.containsKey(port) && running[port]!!)) return@thread
            try { serverSocket = ServerSocket(port) } catch (e: Exception) { return@thread }
            running[port] = true
            acceptingSockets[port] = LinkedHashSet()
            while (running.containsKey(port) && running[port]!!) {
                val acceptedConnection = serverSocket.accept()
                ODLogger.logInfo("RTTServer new connection from ${acceptedConnection.inetAddress.hostAddress}")
                acceptingSockets[port]!!.add(acceptedConnection)
                thread(name = "RTTServer Worker") {
                    try {
                        for (x in 1..numPackets) {
                            if (acceptedConnection != null) {
                                acceptedConnection.getInputStream().read()
                                acceptedConnection.getOutputStream().write(ByteArray(1))
                            }
                        }
                    } catch(e: Exception) {}
                    try { acceptedConnection?.close() } catch (e: Exception) {}

                }
            }
            try {
                serverSocket.close()
            } catch (e: Exception) {}
        }
    }

    fun stop(port: Int) {
        if (running.containsKey(port)) running[port] = false
        if (acceptingSockets.containsKey(port) || acceptingSockets[port] != null) {
            for (connection in acceptingSockets[port]!!) {
                try { connection.close() } catch (e: Exception) {}
            }
        }
        try { serverSocket.close() } catch (e: Exception) {}
    }
}