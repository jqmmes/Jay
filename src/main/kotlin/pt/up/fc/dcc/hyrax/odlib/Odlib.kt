package pt.up.fc.dcc.hyrax.odlib

import pt.up.fc.dcc.hyrax.odlib.grpc.*
import pt.up.fc.dcc.hyrax.odlib.tensorflow.DetectObjects
import java.security.cert.CertPath

/**
 * Public interface for outside communication
 */
class Odlib {

    private lateinit var tfInstance : DetectObjects

    fun main(){
        val client = grpcClient("localhost", 50051)
        grpcServer().startServer()
        client.putJobAsync(1, ByteArray(0))
    }

    private fun initTF() {
        if (!::tfInstance.isInitialized) tfInstance = DetectObjects()
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
}