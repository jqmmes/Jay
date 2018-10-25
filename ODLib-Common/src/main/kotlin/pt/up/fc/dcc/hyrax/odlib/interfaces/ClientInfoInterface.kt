package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.clients.DeviceInformation
import pt.up.fc.dcc.hyrax.odlib.clients.RemoteODClient

interface ClientInfoInterface {
    fun onNewClient(odClient: RemoteODClient)
    fun onDisconectedClient(odClient: RemoteODClient)
    fun onNewClientStatus(clientID: Long, information: DeviceInformation)
}