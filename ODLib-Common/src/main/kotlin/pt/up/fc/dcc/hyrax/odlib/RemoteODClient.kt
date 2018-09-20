package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClient

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

    override fun configureModel() {}

    override fun detectObjects(imgPath: String) : List<ODUtils.ODDetection?>{
        return ODUtils.parseResults(remoteClient.putJobSync(ODService.requestId.incrementAndGet(), ODService.localDetect.getByteArrayFromImage(imgPath)))
    }

    fun putResults(id:Int, results : List<ODUtils.ODDetection?>) {
        remoteClient.putResults(id, results)
    }

    override fun asyncDetectObjects(imgPath: String, callback: (List<ODUtils.ODDetection?>) -> Unit) {
        ODService.putRemoteJobAsync(this, remoteClient, imgPath, callback)
    }
}