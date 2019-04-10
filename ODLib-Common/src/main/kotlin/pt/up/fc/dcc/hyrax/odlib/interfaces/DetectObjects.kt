package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.structures.Detection
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import java.io.File

interface DetectObjects {
    var minimumScore: Float
    val models: List<Model>

    fun extractModel(modelFile: File) : String
    fun downloadModel(model: Model): File?
    fun checkDownloadedModel(name: String): Boolean

    fun loadModel(model: Model, completeCallback: ((ODProto.Status) -> Unit)? = null)
    fun modelLoaded(model: Model): Boolean
    fun setMinAcceptScore(score: Float)
    fun detectObjects(imgPath: String) : List<Detection>
    fun detectObjects(imgData: ByteArray) : List<Detection>
    fun getByteArrayFromImage(imgPath: String) : ByteArray
    fun clean()
}