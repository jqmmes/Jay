package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClient
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob

@Suppress("unused")
class RemoteODClient(private val address: String, private val port: Int) : ODClient() {

    private var models : MutableSet<ODModel> = HashSet()
    private var remoteClient: GRPCClient = GRPCClient(address, port)
    override var id : Long = 0
    private val deviceInformation = DeviceInformation()

    init {
        id = ODUtils.genClientId(address)
    }

    fun getDeviceInformation() : DeviceInformation {
        return deviceInformation
    }

    fun destroy() {
        remoteClient
    }

    override fun getAddress() : String {
        return address
    }

    override fun getPort() : Int {
        return port
    }

    private fun getModels(refresh: Boolean = true) : Set<ODModel> {
        if (refresh) {
            models.clear()
            models.addAll(remoteClient.getModels())
        }
        return models.toSet()
    }

    fun getModelCount(refresh : Boolean = false) : Int {
        return getModels(refresh).count()
    }

    fun ping() : Boolean {
        return remoteClient.ping()
    }

    override fun configureModel() {}

    override fun detectObjects(odJob: ODJob) : List<ODUtils.ODDetection?>{
        return ODUtils.parseResults(remoteClient.putJobSync(odJob.getId(), odJob.getData()))
    }

    fun putResults(id: Long, results : List<ODUtils.ODDetection?>) {
        remoteClient.putResults(id, results)
    }

    override fun asyncDetectObjects(odJob: ODJob, callback: (List<ODUtils.ODDetection?>) -> Unit) {
        remoteClient.putJobAsync(odJob.getId(), odJob.getData(), callback)
    }

    fun sayHello() {
        remoteClient.sayHello()
    }
}