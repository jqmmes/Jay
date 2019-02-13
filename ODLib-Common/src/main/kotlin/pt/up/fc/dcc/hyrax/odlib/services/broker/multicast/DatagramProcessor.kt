package pt.up.fc.dcc.hyrax.odlib.services.broker.multicast

import pt.up.fc.dcc.hyrax.odlib.utils.NetworkUtils
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.net.DatagramPacket

object DatagramProcessor {
    fun process(packet: DatagramPacket) {
        val ois = ObjectInputStream(ByteArrayInputStream(packet.data))
        val message = ois.readObject() as AdvertisingMessage
        /*when (message.msgType) {
            0 -> ClientManager.addOrIgnoreClient(NetworkUtils.getHostAddressFromPacket(packet), ODSettings.brokerPort)
            1 -> {
                val clientID = ODUtils.genClientId(NetworkUtils.getHostAddressFromPacket(packet))
                if (ClientManager.getRemoteODClient(clientID) == null) {
                    ClientManager.addOrIgnoreClient(NetworkUtils.getHostAddressFromPacket(packet), ODSettings.brokerPort)
                }
                ClientManager.updateStatus(clientID, ODUtils.parseDeviceStatus(message.data))
            }
        }*/
    }
}