package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.RemoteODClient

interface DiscoverInterface {
    fun onNewClientFound(remoteClient: RemoteODClient)
}