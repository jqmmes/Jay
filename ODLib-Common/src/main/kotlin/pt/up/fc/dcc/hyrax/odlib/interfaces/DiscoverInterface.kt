package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.RemoteODClient
import java.net.DatagramPacket

interface DiscoverInterface {
    fun onMulticastReceived(packet: DatagramPacket)
}