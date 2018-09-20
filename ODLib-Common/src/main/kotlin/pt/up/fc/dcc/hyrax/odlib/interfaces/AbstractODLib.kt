package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.ODClient
import pt.up.fc.dcc.hyrax.odlib.ODService
import pt.up.fc.dcc.hyrax.odlib.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServer

abstract class AbstractODLib (val localDetector : DetectObjects) {

    companion object {
        private var remoteClients : MutableSet<RemoteODClient> = HashSet()

        fun getClient(address: String, port: Int): RemoteODClient? {
            for (client in remoteClients) {
                if (client.getAdress() == address && client.getPort() == port)
                    return client
            }
            return null
        }
    }

    private var localClient : ODClient = ODClient()
    //private var odService : ODService? = null
    private var grpcServer : GRPCServer? = null
    private var nextJobId : Int = 0

    /*abstract fun setDetector(localDetector : DetectObjects) {
        this.localDetector = localDetector
    }*/

    init {
        //localClient.setDetector(localDetector)
    }

    fun setTFModel(modelPath: String) {
        localDetector.loadModel(modelPath)
    }

    fun setTFModelMinScore(minimumScore: Float) {
        localDetector.setMinAcceptScore(minimumScore)
    }

    fun getClient() : ODClient {
        return localClient
    }

    fun newRemoteClient(address: String, port : Int) : RemoteODClient {
        return RemoteODClient(address, port)
    }

    fun addRemoteClient(client: RemoteODClient) {
        remoteClients.add(client)
    }

    fun addRemoteClients(clients: List<RemoteODClient>) {
        remoteClients.addAll(clients)
    }

    fun getRemoteClients() : List<RemoteODClient> {
        return remoteClients.toList()
    }

    fun removeRemoteClient(client: RemoteODClient) {
        remoteClients.remove(client)
    }

    fun startODService() {
        //odService = ODService(localDetector).startService()
        ODService.startService(localDetector)
    }

    fun stopODService() {
        //odService?.stop()
        ODService.stop()
        //odService = null
    }

    fun startGRPCServer(port : Int) {
        if (grpcServer == null) {
            if (!ODService.isRunning()) startODService()
            grpcServer = GRPCServer(port).startServer()
        }
    }

    fun stopGRPCServer() {
        grpcServer?.stop()
        grpcServer = null
    }

    fun clean() {
        stopODService()
        stopGRPCServer()
        remoteClients.clear()
    }

}