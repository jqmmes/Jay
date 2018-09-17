package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.ODService
import pt.up.fc.dcc.hyrax.odlib.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServer

abstract class AbstractODLib(var localDetector : DetectObjects) {

    private var remoteClients : MutableSet<RemoteODClient> = HashSet()
    private var odService : ODService? = null
    private var grpcServer : GRPCServer? = null


    fun setTFModel(modelPath: String) {
        localDetector.setModel(modelPath)
    }

    fun setTFLabels(labelPath: String) {
        localDetector.setLabels(labelPath)
    }

    fun setTFModelMinScore(minimumScore: Float) {
        localDetector.setScore(minimumScore)
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

    abstract fun detectObjects(image: String)

    abstract fun detectObjects(image: String, remoteODClient: RemoteODClient)

    abstract fun asyncDetectObjects(image: String, callback: ODCallback)

    abstract fun asyncDetectObjects(image: String, remoteODClient: RemoteODClient, callback: RemoteODCallback)

    fun startODService() {
        odService = ODService(localDetector).startService()
    }

    fun stopODService() {
        odService?.stop()
        odService = null
    }

    fun startGRPCServer(port : Int) {
        if (grpcServer == null) {
            if (odService == null) startODService()
            grpcServer = GRPCServer(port, odService!!).start()
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