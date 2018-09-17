package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClient

open class RemoteODClient(private val address: String, private val port: Int) {

    private var models : MutableSet<Model> = HashSet()
    private var remoteClient: GRPCClient = GRPCClient(address, port)


    fun getAdress() : String {
        return address
    }

    fun getPort() : Int {
        return port
    }

    fun getModels() : List<Model> {
        models = remoteClient.getModels()
        return models.toList()
    }

    fun getModelCount() : Int {
        return getModels().count()
    }
}