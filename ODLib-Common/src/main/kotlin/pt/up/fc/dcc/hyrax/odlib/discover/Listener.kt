package pt.up.fc.dcc.hyrax.odlib.discover

import pt.up.fc.dcc.hyrax.odlib.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODLib
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

class Listener {
    fun listen() {
        val mcPort = 50000
        val mcIPStr = "224.0.0.0"
        val mcSocket: MulticastSocket?
        val mcIPAddress: InetAddress?
        mcIPAddress = InetAddress.getByName(mcIPStr)
        mcSocket = MulticastSocket(mcPort)
        println("Multicast Receiver running at:" + mcSocket.localSocketAddress)
        mcSocket.joinGroup(mcIPAddress!!)

        val packet = DatagramPacket(ByteArray(1024), 1024)

        println("Waiting for a  multicast message...")
        mcSocket.receive(packet)
        ODLib.addRemoteClient(RemoteODClient(packet.address.hostAddress, 50051))
        println(packet.address.address)
        /*val msg = String(packet.data, packet.offset,
                packet.length)*/
        //println("[Multicast  Receiver] Received:$msg")

        mcSocket.leaveGroup(mcIPAddress)
        mcSocket.close()
    }
}
