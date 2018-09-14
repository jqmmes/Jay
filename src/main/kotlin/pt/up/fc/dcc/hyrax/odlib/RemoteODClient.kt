package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.grpc.grpcClient

class RemoteODClient {

    private var models : List<ODLib.Model> = emptyList()
    private lateinit var grpcClient: grpcClient


    fun getModels() : List<ODLib.Model> {
        return emptyList()
    }


}