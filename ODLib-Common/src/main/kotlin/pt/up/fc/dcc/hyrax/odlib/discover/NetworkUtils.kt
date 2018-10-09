package pt.up.fc.dcc.hyrax.odlib.discover

import pt.up.fc.dcc.hyrax.odlib.AbstractODLib
import pt.up.fc.dcc.hyrax.odlib.ODLogger
import java.net.DatagramPacket
import java.net.NetworkInterface

class NetworkUtils {
    companion object {
        internal inline fun <reified T>getCompatibleInterfaces(): List<NetworkInterface> {
            val interfaceList : MutableList<NetworkInterface> = mutableListOf()
            for (netInt in NetworkInterface.getNetworkInterfaces()) {
                if (!netInt.isLoopback && !netInt.isPointToPoint && netInt.isUp && netInt.supportsMulticast()) {
                    for (address in netInt.inetAddresses)
                        if (address is T) {
                            ODLogger.logInfo("Available Multicast interface: ${netInt.name}")
                            interfaceList.add(netInt)
                        }
                }
            }
            return interfaceList
        }

        fun getHostAddressFromPacket(packet: DatagramPacket) : String {
            return packet.address.hostAddress.substringBefore("%")
        }
    }
}