package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.interfaces.ClientInfoInterface
import pt.up.fc.dcc.hyrax.odlib.status.StatusManager
import pt.up.fc.dcc.hyrax.odlib.utils.DeviceInformation
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.util.concurrent.ConcurrentHashMap

@Suppress("unused")
object ClientManager {

    private var clientInfoCallback: ClientInfoInterface? = null
    private var cloud: CloudODClient = CloudODClient()
    private val remoteODClients = ConcurrentHashMap<Long, RemoteODClient>()
    private val NEW_CLIENT_LOCK = Object()


    fun addOrIgnoreClient(Ip: String, port: Int) {
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
                    StatusManager.setConnections(remoteODClients.minus(0L).size)
                } else {
                    newClient!!.destroy()
                }
            }
        }
        //if (sayHello && isNewClient) newClient!!.sayHello()
        if (isNewClient && clientInfoCallback != null && newClient != null) { clientInfoCallback!!.onNewClient(newClient!!) }
    }

    fun setClientInfoCallback(clientInfoCallback: ClientInfoInterface) {
        this.clientInfoCallback = clientInfoCallback
    }

    fun getLocalODClient(): RemoteODClient {
        if (!remoteODClients.contains(0L)) addOrIgnoreClient("localhost", ODSettings.brokerPort)
        return remoteODClients[0L]!!
    }

    fun changeCloudClient(cloudODClient: CloudODClient) {
        cloud = cloudODClient
    }

    fun getCloudClient(): CloudODClient {
        return cloud
    }

    fun getRemoteODClient(id: Long): RemoteODClient? {
        if (id == 0L) return getLocalODClient()
        @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER") var client: RemoteODClient? = null
        synchronized(NEW_CLIENT_LOCK) {
            client = remoteODClients[id]
        }
        return client
    }

    fun getRemoteODClients(includeLocal: Boolean = false): List<RemoteODClient> {
        if (!includeLocal && remoteODClients.containsKey(0L)) return (remoteODClients.minus(0L)).values.toList()
        if (!includeLocal) return remoteODClients.values.toList()
        return remoteODClients.values.toList()
    }

    fun updateStatus(clientID: Long, deviceInformation: DeviceInformation) {
        if(clientInfoCallback!=null) clientInfoCallback!!.onNewClientStatus(clientID, deviceInformation)
    }
}