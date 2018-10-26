package pt.up.fc.dcc.hyrax.odlib.utils

import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    private var localIPv4: String = ""
    private var firstRefresh: Boolean = true

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



    fun getLocalIpV4(refresh: Boolean = false): String {
        if (refresh || firstRefresh) {
            firstRefresh = false
            localIPv4 = ""
            val interfaces = getCompatibleInterfaces<Inet4Address>()
            if (!interfaces.isEmpty()) {
                for (ip in interfaces[0].inetAddresses) {
                    if (ip is Inet4Address) localIPv4 = ip.toString().trim('/')
                }
            }
        }
        return localIPv4
    }

    fun getHostAddressFromPacket(packet: DatagramPacket) : String {
        return packet.address.hostAddress.substringBefore("%")
    }
}