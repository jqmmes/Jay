package pt.up.fc.dcc.hyrax.odlib.interfaces

import java.net.DatagramPacket

interface DiscoverInterface {
    fun onMulticastReceived(packet: DatagramPacket)
}