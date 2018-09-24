package pt.up.fc.dcc.hyrax.odlib

import io.grpc.StatusRuntimeException
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClient
import java.lang.NullPointerException

class RemoteODClient(private val address: String, private val port: Int) : ODClient() {

    private var models : MutableSet<ODModel> = HashSet()
    private var remoteClient: GRPCClient = GRPCClient(address, port)


    override fun getAdress() : String {
        return address
    }

    override fun getPort() : Int {
        return port
    }

    fun getModels(refresh: Boolean = true) : Set<ODModel> {
        if (refresh) {
            models.clear()
            models.addAll(remoteClient.getModels())
        }
        return models.toSet()
    }

    fun getModelCount(refresh : Boolean = false) : Int {
        return getModels(refresh).count()
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
        ODService.putRemoteJobAsync(ODClient(), remoteClient, imgPath, callback)
    }

    fun sayHello() {
        remoteClient.sayHello()
    }
}