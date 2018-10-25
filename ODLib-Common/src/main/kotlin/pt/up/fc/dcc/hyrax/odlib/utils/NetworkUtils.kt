package pt.up.fc.dcc.hyrax.odlib.utils

import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkUtils {
    companion object {
        internal inline fun <reified T>getCompatibleInterfaces(): List<NetworkInterface> {
            val interfaceList : MutableList<NetworkInterface> = mutableListOf()
            for (netInt in NetworkInterface.getNetworkInterfaces()) {
                if (!netInt.isLoopback && !netInt.isPointToPoint && netInt.isUp && netInt.supportsMulticast()) {
                    for (address in netInt.inetAddresses)
                        if (address is T) {
                            //ODLogger.logInfo("Available Multicast interface: ${netInt.name}")
                            interfaceList.add(netInt)
                        }
                }
            }
            return interfaceList
        }

        fun getLocalIpV4() : String {
            val interfaces = getCompatibleInterfaces<Inet4Address>()
            if (interfaces.isEmpty()) return ""
            for (ip in interfaces[0].inetAddresses) {
                if (ip is Inet4Address) return ip.toString().trim('/')
            }
            return ""
        }

        fun getHostAddressFromPacket(packet: DatagramPacket) : String {
            return packet.address.hostAddress.substringBefore("%")
        }
    }
}