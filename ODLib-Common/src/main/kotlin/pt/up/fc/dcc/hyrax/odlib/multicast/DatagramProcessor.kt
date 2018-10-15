package pt.up.fc.dcc.hyrax.odlib.multicast

import pt.up.fc.dcc.hyrax.odlib.clients.ClientManager
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.utils.NetworkUtils
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import java.net.DatagramPacket

object DatagramProcessor {

    fun process(packet: DatagramPacket) {
        ClientManager.addOrIgnoreClient(RemoteODClient(NetworkUtils.getHostAddressFromPacket(packet),
                ODSettings.serverPort))
    }
}