package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ClientManager {

    private val me: ODClient = ODClient()
    private val remoteODClients = ConcurrentHashMap<Long, ODClient>()

    fun addOrIgnoreClient(remoteODClient: RemoteODClient) {
        if (!remoteODClients.containsKey(remoteODClient.id)) {
            ODLogger.logInfo("new Client found ${remoteODClient.id}")
            remoteODClients[remoteODClient.id] = remoteODClient
            remoteODClient.sayHello()
        }
    }

    fun getLocalODClient(): ODClient {
        return me
    }

    fun getRemoteODClient(id: Long): RemoteODClient? {
        println("getRemoteClient $id")
        return remoteODClients[id] as RemoteODClient
    }

    fun getRemoteODClients() : Enumeration<ODClient>? {
        return remoteODClients.elements()
    }
}