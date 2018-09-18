package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.ODService
import pt.up.fc.dcc.hyrax.odlib.RemoteODClient
import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServer
import java.util.*

abstract class AbstractODLib(var localDetector : DetectObjects) {

    private var remoteClients : MutableSet<RemoteODClient> = HashSet()
    private var odService : ODService? = null
    private var grpcServer : GRPCServer? = null
    private var nextJobId : Int = 0


    fun setTFModel(modelPath: String) {
        localDetector.setModel(modelPath)
    }

    fun setTFLabels(labelPath: String) {
        localDetector.setLabels(labelPath)
    }

    fun setTFModelMinScore(minimumScore: Float) {
        localDetector.setScore(minimumScore)
    }

    fun addRemoteClient(client: RemoteODClient) {
        remoteClients.add(client)
    }

    fun addRemoteClients(clients: List<RemoteODClient>) {
        remoteClients.addAll(clients)
    }

    fun getRemoteClients() : List<RemoteODClient> {
        return remoteClients.toList()
    }

    fun removeRemoteClient(client: RemoteODClient) {
        remoteClients.remove(client)
    }

    fun detectObjects(imgPath: String) {
        //localDetector.detectObjects(imgPath)
        if (odService == null) startODService()
        odService!!.putJob(imgPath)
    }

    fun detectObjects(imgPath: String, remoteODClient: RemoteODClient) : Int {
        val jobId = nextJobId++
        remoteODClient.sendJob(jobId, localDetector.getByteArrayFromImage(imgPath))
        return jobId
    }

    fun asyncDetectObjects(imgPath: String, callback: ODCallback) {
        if (odService == null) startODService()
        odService!!.putJob(imgPath, callback)
    }

    fun asyncDetectObjects(imgPath: String, remoteODClient: RemoteODClient, callback: RemoteODCallback) : Int {
        if (odService == null) startODService()
        val jobId = detectObjects(imgPath, remoteODClient)
        odService!!.waitResultsForTask(jobId, callback)
        return jobId
    }

    fun startODService() {
        odService = ODService(localDetector).startService()
    }

    fun stopODService() {
        odService?.stop()
        odService = null
    }

    fun startGRPCServer(port : Int) {
        if (grpcServer == null) {
            if (odService == null) startODService()
            grpcServer = GRPCServer(port, odService!!).start()
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