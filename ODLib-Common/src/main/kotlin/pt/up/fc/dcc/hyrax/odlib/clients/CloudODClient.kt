package pt.up.fc.dcc.hyrax.odlib.clients

import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClient
import pt.up.fc.dcc.hyrax.odlib.jobManager.ODJob
import pt.up.fc.dcc.hyrax.odlib.utils.ODSettings
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils

class CloudODClient(address: String = ODSettings.cloudIp, port: Int = ODSettings.serverPort) : RemoteODClient() {
    private var remoteClient: GRPCClient = GRPCClient(address, port)

    override fun detectObjects(odJob: ODJob): List<ODUtils.ODDetection?> {
        val result = remoteClient.putJobCloudSync(odJob.getId(), odJob.getData())
        if (result.first)
            return remoteClient.putJobCloudSync(odJob.getId(), odJob.getData()).second
        return emptyList()
    }

    override fun asyncDetectObjects(odJob: ODJob, callback: (List<ODUtils.ODDetection?>) -> Unit) {
        remoteClient.putJobCloudAsync(odJob.getId(), odJob.getData(), callback)
    }
}