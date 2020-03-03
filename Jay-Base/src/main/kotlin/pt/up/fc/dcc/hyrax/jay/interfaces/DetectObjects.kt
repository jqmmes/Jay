package pt.up.fc.dcc.hyrax.jay.interfaces

import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Status
import pt.up.fc.dcc.hyrax.jay.structures.Detection
import pt.up.fc.dcc.hyrax.jay.structures.Model
import java.io.File

interface DetectObjects {
    var useNNAPI: Boolean
    var minimumScore: Float
    val models: List<Model>
    var useGPU: Boolean

    fun extractModel(modelFile: File) : String
    fun downloadModel(model: Model): File?
    fun checkDownloadedModel(name: String): Boolean

    fun loadModel(model: Model, completeCallback: ((Status) -> Unit)? = null)
    fun modelLoaded(model: Model): Boolean
    fun setMinAcceptScore(score: Float)
    fun detectObjects(imgPath: String) : List<Detection>
    fun detectObjects(imgData: ByteArray) : List<Detection>
    fun getByteArrayFromImage(imgPath: String) : ByteArray
    fun clean()
}