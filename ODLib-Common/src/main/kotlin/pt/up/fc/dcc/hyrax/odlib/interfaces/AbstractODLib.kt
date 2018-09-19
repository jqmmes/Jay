package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.ODClient
import pt.up.fc.dcc.hyrax.odlib.ODService
import pt.up.fc.dcc.hyrax.odlib.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServer

abstract class AbstractODLib(var localDetector : DetectObjects) {

    private var remoteClients : MutableSet<RemoteODClient> = HashSet()
    private var localClient : ODClient = ODClient()
    //private var odService : ODService? = null
    private var grpcServer : GRPCServer? = null
    private var nextJobId : Int = 0

    init {
        //localClient.setDetector(localDetector)
    }

    fun setTFModel(modelPath: String) {
        localDetector.setModel(modelPath)
    }

    fun setTFLabels(labelPath: String) {
        localDetector.setLabels(labelPath)
    }

    fun setTFModelMinScore(minimumScore: Float) {
        localDetector.setScore(minimumScore)
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