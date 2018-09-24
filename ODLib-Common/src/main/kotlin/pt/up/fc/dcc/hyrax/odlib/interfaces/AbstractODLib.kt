package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.ODClient
import pt.up.fc.dcc.hyrax.odlib.ODModel
import pt.up.fc.dcc.hyrax.odlib.ODService
import pt.up.fc.dcc.hyrax.odlib.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServer

abstract class AbstractODLib (val localDetector : DetectObjects) {


    private var localClient : ODClient = ODClient()
    private var grpcServer : GRPCServer? = null
    private var nextJobId : Int = 0

    companion object {
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
        //odService = ODService(localDetector).startService()
        ODService.startService(localDetector)
    }

    fun stopODService() {
        //odService?.stop()
        ODService.stop()
        //odService = null
    }

    fun startGRPCServer(odLib: AbstractODLib, port : Int) {
        serverPort = port
        if (grpcServer == null) {
            if (!ODService.isRunning()) startODService()
            grpcServer = GRPCServer.startServer(odLib, port)
        }
    }

    fun startGRPCServerService(odLib: AbstractODLib, port : Int) {
        serverPort = port
        if (grpcServer == null) {
            if (!ODService.isRunning()) startODService()
            grpcServer = GRPCServer(odLib, port).start()
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