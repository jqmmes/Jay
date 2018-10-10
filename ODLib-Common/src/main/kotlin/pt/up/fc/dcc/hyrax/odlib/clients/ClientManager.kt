package pt.up.fc.dcc.hyrax.odlib.clients

import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ClientManager {

    private val me: ODClient = ODClient()
    private val remoteODClients = ConcurrentHashMap<Long, ODClient>()

    fun addOrIgnoreClient(remoteODClient: RemoteODClient) {
        if (!remoteODClients.containsKey(remoteODClient.id)) remoteODClients[remoteODClient.id] = remoteODClient
    }

    fun getLocalODClient(): ODClient {
        return me
    }

    fun getRemoteODClient(id: Long): RemoteODClient? {
        return remoteODClients[id] as RemoteODClient
    }

    fun getRemoteODClients() : Enumeration<ODClient>? {
        return remoteODClients.elements()
    }
}