package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ClientManager {

    private val me: RemoteODClient = RemoteODClient()
    private var cloud: CloudODClient = CloudODClient()
    private val remoteODClients = ConcurrentHashMap<Long, RemoteODClient>()
    private val NEW_CLIENT_LOCK = Object()


    fun addOrIgnoreClient(Ip: String, port: Int, sayHello: Boolean = false) {
        val clientId = ODUtils.genClientId(Ip)
        var isNewClient = false
        var newClient : RemoteODClient? = null
        synchronized(NEW_CLIENT_LOCK) {
            if (!remoteODClients.containsKey(clientId)) {
                ODLogger.logInfo("new Client found $clientId")
                newClient = RemoteODClient(if (clientId != 0L) Ip else "localhost", port)
                if (newClient!!.ping()) {
                    remoteODClients[clientId] = newClient!!
                    isNewClient = true
                } else {
                    newClient!!.destroy()
                }
            }
        }
        if (sayHello && isNewClient) newClient!!.sayHello()
    }

    fun getLocalODClient(): RemoteODClient {
        return me
    }

    fun changeCloudClient(cloudODClient: CloudODClient) {
        cloud = cloudODClient
    }

    fun getCloudClient(): CloudODClient {
        return cloud
    }

    fun getRemoteODClient(id: Long): RemoteODClient? {
        return remoteODClients[id] as RemoteODClient
    }

    fun getRemoteODClients() : Enumeration<RemoteODClient>? {
        return remoteODClients.elements()
    }
}