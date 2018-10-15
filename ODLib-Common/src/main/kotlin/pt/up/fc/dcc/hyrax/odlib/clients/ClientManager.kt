package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ClientManager {

    private val me: ODClient = ODClient()
    private val remoteODClients = ConcurrentHashMap<Long, ODClient>()
    private val NEW_CLIENT_LOCK = Object()


    fun addOrIgnoreClient(Ip: String, port: Int, sayHello: Boolean = false) {
        val clientId = ODUtils.genClientId(Ip)
        var isNewClient = false
        var newClient : RemoteODClient? = null
        synchronized(NEW_CLIENT_LOCK) {
            if (!remoteODClients.containsKey(clientId)) {
                ODLogger.logInfo("new Client found $clientId")
                newClient = RemoteODClient(Ip, port)
                remoteODClients[clientId] = newClient!!
                isNewClient = true
            }
        }
        if (sayHello && isNewClient) newClient!!.sayHello()
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