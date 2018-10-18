package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClient
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

class CloudODClient(address: String = ODSettings.cloudIp, port: Int = ODSettings.serverPort) : ODClient() {
    private var models : MutableSet<ODModel> = HashSet()
    private var remoteClient: GRPCClient = GRPCClient(address, port)
    private val deviceInformation = DeviceInformation()

    init {
        id = ODUtils.genClientId(address)
    }

    override fun detectObjects(odJob: ODJob): List<ODUtils.ODDetection?> {
        return remoteClient.putJobCloudSync(odJob.getId(), odJob.getData())
    }

    override fun asyncDetectObjects(odJob: ODJob, callback: (List<ODUtils.ODDetection?>) -> Unit) {
        remoteClient.putJobCloudAsync(odJob.getId(), odJob.getData(), callback)
    }

    override fun getAddress(): String {
        return ODSettings.cloudIp
    }

    fun ping() : Boolean {
        return remoteClient.ping()
    }

    fun sayHello() {
        remoteClient.sayHello()
    }
}