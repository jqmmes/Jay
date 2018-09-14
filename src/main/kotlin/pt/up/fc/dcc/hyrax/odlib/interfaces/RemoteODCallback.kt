package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.RemoteODClient

interface RemoteODCallback {
    fun onNewResult(client: RemoteODClient)
}