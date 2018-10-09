package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServer
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODLog

abstract class AbstractODLib (val localDetector : DetectObjects) {


    private var localClient : ODClient = ODClient()
    private var grpcServer : GRPCServer? = null
    private var nextJobId : Int = 0

    companion object {
        var log : Boolean = false
        lateinit var loggingConsole : ODLog

        fun log(message : String) {
            if (log) loggingConsole.log(message)
        }

        private var remoteClients : MutableSet<RemoteODClient> = HashSet()

        private var serverPort : Int = 0

        fun getServerPort() : Int{
            return serverPort
        }

        fun getClient(address: String, port: Int): RemoteODClient? {
            println("Searching for client $port")
            for (client in remoteClients) {
                println(client.getPort())
                if (client.getAdress() == address && client.getPort() == port)
                    return client
            }
            return null
        }

        fun addRemoteClient(client: RemoteODClient) {
            println("add remote Client " + client.getPort())
            remoteClients.add(client)
        }

        fun addRemoteClients(clients: List<RemoteODClient>) {
            remoteClients.addAll(clients)
        }
    }

    abstract fun getDetector() : DetectObjects

    fun enableLogs(loggingInterface : ODLog){
        log = true
        loggingConsole = loggingInterface
    }

    fun listModels(onlyLoaded: Boolean = true) : Set<ODModel> {
        if (!onlyLoaded) return localDetector.models.toSet()
        val result = HashSet<ODModel>()
        for (model in localDetector.models)
            if (model.downloaded) result.add(model)
        return result
    }


    fun setTFModel(model: ODModel) {
        localDetector.loadModel(model)
    }

    fun setTFModelMinScore(minimumScore: Float) {
        localDetector.setMinAcceptScore(minimumScore)
    }

    fun getClient() : ODClient {
        return localClient
    }

    fun newRemoteClient(address: String, port : Int) : RemoteODClient {
        val remoteODClient = RemoteODClient(address, port)
        addRemoteClient(remoteODClient)
        return remoteODClient
    }


    fun getRemoteClients() : List<RemoteODClient> {
        return remoteClients.toList()
    }

    fun removeRemoteClient(client: RemoteODClient) {
        remoteClients.remove(client)
    }

    fun startODService() {
        ODService.startService(localDetector)
    }

    fun stopODService() {
        ODService.stop()
    }

    fun startGRPCServer(odLib: AbstractODLib, port : Int) {
        serverPort = port
        if (grpcServer == null) {
            if (!ODService.isRunning()) startODService()
            grpcServer = GRPCServer.startServer(odLib, port)
        }
    }

    fun startGRPCServerService(odLib: AbstractODLib, port : Int, useNettyServer : Boolean = false) {
        serverPort = port
        if (grpcServer == null) {
            if (!ODService.isRunning()) startODService()
            grpcServer = GRPCServer(odLib, port, useNettyServer).start()
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