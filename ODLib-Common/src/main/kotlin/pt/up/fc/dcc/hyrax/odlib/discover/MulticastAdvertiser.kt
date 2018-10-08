package pt.up.fc.dcc.hyrax.odlib.discover

import pt.up.fc.dcc.hyrax.odlib.interfaces.ODLib
import java.lang.Thread.sleep
import java.net.*
import kotlin.concurrent.thread

class MulticastAdvertiser {
    companion object {
        private var running = false
        private lateinit var mcSocket : MulticastSocket
        private lateinit var mcIPAddress : InetAddress

        fun advertise() {
            if (running) {
                ODLib.log("MulticastServer already running")
                return
            }

            thread(isDaemon=true) {
                val mcPort = 50000
                val mcIPStr = "224.0.0.0"
                //val mcIPStr = "FF7E:230::1234"
                running = true
                mcSocket = MulticastSocket()
                mcSocket.loopbackMode = true
                val msg = ByteArray(1)
                mcIPAddress = Inet4Address.getByName(mcIPStr)
                println(mcIPAddress)
                mcSocket.joinGroup(mcIPAddress)

                val packet = DatagramPacket(msg, 1, mcIPAddress, mcPort)

                do {
                    ODLib.log("Sending Multicast packet")
                    mcSocket.send(packet)
                    sleep(1000)
                } while (running)

                mcSocket.leaveGroup(mcIPAddress)
                mcSocket.close()
            }
        }

        fun stop() {
            running = false
        }
    }
}