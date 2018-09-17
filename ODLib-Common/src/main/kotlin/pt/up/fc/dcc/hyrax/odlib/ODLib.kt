//package pt.up.fc.dcc.hyrax.odlib
//
//import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCClient
//import pt.up.fc.dcc.hyrax.odlib.grpc.GRPCServer
//import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
//import pt.up.fc.dcc.hyrax.odlib.interfaces.ODCallback
//import pt.up.fc.dcc.hyrax.odlib.interfaces.AbstractODLib
//import pt.up.fc.dcc.hyrax.odlib.interfaces.RemoteODCallback
//
///**
// * Public interface for outside communication
// */
//class AbstractODLib(tfInstance: DetectObjects) : AbstractODLib(tfInstance) {
//
//
//    //private lateinit var tfInstance : cloudletDetectObjects
//
//    fun main(){
//        val client = GRPCClient("localhost", 50051)
//        GRPCServer().startServer()
//        client.putJobAsync(1, ByteArray(0))
//    }
//
//    /*private fun initTF() {
//        if (!::tfInstance.isInitialized) tfInstance = cloudletDetectObjects()
//    }*/
//
//    /*public override fun setTFModel(modelPath: String) {
//        //initTF()
//        tfInstance.setModel(modelPath)
//    }
//
//    public override fun setTFLabels(labelPath: String) {
//        //initTF()
//        tfInstance.setTFLabels(labelPath)
//    }
//
//    public override fun setTFModelMinScore(minimumScore: Float) {
//        //initTF()
//        tfInstance.setScore(minimumScore)
//    }*/
//
//    public override fun addRemoteClient(client: RemoteODClient) {
//
//    }
//
//    /*public override fun getClient() : RemoteODClient {
//        return RemoteODClient()
//    }
//
//    public override fun getRemoteClients() : List<RemoteODClient> {
//        return emptyList()
//    }*/
//
//    public override fun removeRemoteClient() {
//
//    }
//
//    public override fun detectObjects(image: String) {
//
//    }
//
//    public override fun detectObjects(image: String, remoteODClient: RemoteODClient) {
//
//    }
//
//    public override fun asyncDetectObjects(image: String, callback: ODCallback) {
//
//    }
//
//    public override fun asyncDetectObjects(image: String, remoteODClient: RemoteODClient, callback: RemoteODCallback) {
//
//    }
//}