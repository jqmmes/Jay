package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.grpc.*
import pt.up.fc.dcc.hyrax.odlib.interfaces.ODCallback
import pt.up.fc.dcc.hyrax.odlib.interfaces.RemoteODCallback
import pt.up.fc.dcc.hyrax.odlib.tensorflow.cloudletDetectObjects

/**
 * Public interface for outside communication
 */
class Odlib {

    private lateinit var tfInstance : cloudletDetectObjects

    fun main(){
        val client = grpcClient("localhost", 50051)
        grpcServer().startServer()
        client.putJobAsync(1, ByteArray(0))
    }

    private fun initTF() {
        if (!::tfInstance.isInitialized) tfInstance = cloudletDetectObjects()
    }

    public fun setTFModel(modelPath: String) {
        initTF()
        tfInstance.setModel(modelPath)
    }

    public fun setLabels(labelPath: String) {
        initTF()
        tfInstance.setLabels(labelPath)
    }

    public fun setModelMinScore(minimumScore: Float) {
        initTF()
        tfInstance.setScore(minimumScore)
    }

    public fun addClient(client: RemoteODClient) {

    }

    public fun getClient() : RemoteODClient {
        return RemoteODClient()
    }

    public fun getClients() : List<RemoteODClient> {
        return emptyList()
    }

    public fun removeClient() {

    }

    public fun detectObjects(image: String) {

    }

    public fun detectObjects(image: String, remoteODClient: RemoteODClient) {

    }

    public fun asyncDetectObjects(image: String, callback: ODCallback) {

    }

    public fun asyncDetectObjects(image: String, remoteODClient: RemoteODClient, callback: RemoteODCallback) {

    }
}