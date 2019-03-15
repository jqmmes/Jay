package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.structures.ODDetection
import pt.up.fc.dcc.hyrax.odlib.structures.ODModel
import java.io.File

interface DetectObjects {
    var minimumScore: Float
    val models: List<ODModel>

    fun extractModel(modelFile: File) : String
    fun downloadModel(model: ODModel): File?
    fun checkDownloadedModel(name: String): Boolean

    fun loadModel(model: ODModel, completeCallback: ((ODProto.Status) -> Unit)? = null)
    fun modelLoaded(model: ODModel): Boolean
    fun setMinAcceptScore(score: Float)
    fun detectObjects(imgPath: String) : List<ODDetection>
    fun detectObjects(imgData: ByteArray) : List<ODDetection>
    fun getByteArrayFromImage(imgPath: String) : ByteArray
    fun clean()
}