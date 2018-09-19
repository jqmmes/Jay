package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClient
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODCallback

class RemoteODClient(private val address: String, private val port: Int) : ODClient() {

    private var models : MutableSet<ODModel> = HashSet()
    private var remoteClient: GRPCClient = GRPCClient(address, port)


    fun getAdress() : String {
        return address
    }

    fun getPort() : Int {
        return port
    }

    fun getModels() : List<ODModel> {
        models = remoteClient.getModels()
        return models.toList()
    }

    fun getModelCount() : Int {
        return getModels().count()
    }

    fun ping(){
        remoteClient.ping()
    }

    override fun configureModel() {

    }

    override fun detectObjects(imgPath: String) : List<ODUtils.ODDetection?>{
        return ODService.putRemoteJob(remoteClient, imgPath)
    }

    override fun asyncDetectObjects(imgPath: String, callback: ODCallback) {

    }

}