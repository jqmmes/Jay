package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClient
import pt.up.fc.dcc.hyrax.odlib.utils.*

@Deprecated("Will be Client")
@Suppress("unused")
open class RemoteODClient {
    private var address: String
    private var port: Int = ODSettings.brokerPort
    private var models : MutableSet<ODModel> = HashSet()
    private lateinit var remoteClient: GRPCClient
    private var id : Long = 0
    private var deviceInformation = DeviceInformation()

    constructor() {
        this.address = "localhost"
        setLocalVars()
    }

    constructor(address: String, port: Int = ODSettings.brokerPort) {
        this.address = address
        this.port = port
        setLocalVars()
    }

    private fun setLocalVars() {
        id = ODUtils.genClientId(address)
        remoteClient = GRPCClient(address, port)
    }

    fun getDeviceInformation() : DeviceInformation {
        return deviceInformation
    }

    fun setDeviceInformation(information: DeviceInformation) {
        this.deviceInformation = information
    }

    fun destroy() {
        remoteClient
    }

    fun getAddress() : String {
        return address
    }

    fun getPort() : Int {
        return port
    }

    fun getModels(onlyLoaded: Boolean, refresh: Boolean = true) : Set<ODModel> {
        if (refresh) {
            val newModels = remoteClient.getModels()
            if (newModels.first) {
                models.clear()
                models.addAll(newModels.second)
            }
        }
        if (onlyLoaded) {
            val result = HashSet<ODModel>()
            for (model in models)
                if (model.downloaded) result.add(model)
            return result
        }
        return models.toSet()
    }

    fun getModelCount(refresh : Boolean = false) : Int {
        return getModels(false, refresh).count()
    }

    fun ping() : Boolean {
        return remoteClient.ping()
    }

    fun configureModel(minimumScore: Float = 0.3f) {
        remoteClient.configureModel(ODUtils.genModelConfig(hashMapOf("minScore" to minimumScore.toString())))
    }

    open fun detectObjects(odJob: ODJob) : List<ODDetection?>{
        //return ODUtils.parseResults(remoteClient.putJobSync(odJob.getId(), odJob.getData()))
        return ODUtils.parseResults(null)
    }

    fun putResults(id: Long, results : List<ODDetection?>) {
        remoteClient.putResults(id, results)
    }

    open fun asyncDetectObjects(odJob: ODJob, callback: (List<ODDetection?>) -> Unit) {
        //remoteClient.putJobAsync(odJob.getId(), odJob.getData(), callback)
        //remoteClient.putJobCloudAsync(odJob.getId(), odJob.getData(), callback)
    }

    fun sayHello() {
        remoteClient.sayHello()
    }

    fun getId(): Long {
        return id
    }

    fun selectModel(model: ODModel) {
        remoteClient.selectModel(model)
    }

    fun modelLoaded(model: ODModel): Boolean {
        return remoteClient.modelLoaded(model)
    }

    fun getDeviceStatus() : DeviceInformation {
        return remoteClient.getStatus() ?: DeviceInformation()
    }
}