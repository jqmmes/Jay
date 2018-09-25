package pt.up.fc.dcc.hyrax.odlib.discover

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class Advertiser {
    fun advertise() {
        val mcPort = 50000
        val mcIPStr = "224.0.0.0"
        val udpSocket = DatagramSocket()

        val mcIPAddress = InetAddress.getByName(mcIPStr)
        val msg = ByteArray(1)
        val packet = DatagramPacket(msg, 1)
        packet.setAddress(mcIPAddress)
        packet.setPort(mcPort)
        udpSocket.send(packet)
        udpSocket.close()
    }
}