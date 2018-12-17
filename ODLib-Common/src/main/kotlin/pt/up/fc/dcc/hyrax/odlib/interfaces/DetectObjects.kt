package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import java.io.File

interface DetectObjects {
    var minimumScore: Float
    val models: List<ODModel>

    fun extractModel(modelFile: File) : String
    fun downloadModel(model: ODModel): File?
    fun checkDownloadedModel(name: String): Boolean

    fun loadModel(model: ODModel)
    fun modelLoaded(model: ODModel): Boolean
    fun setMinAcceptScore(score: Float)
    fun detectObjects(imgPath: String) : List<ODDetection>
    fun detectObjects(imgData: ByteArray) : List<ODDetection>
    fun getByteArrayFromImage(imgPath: String) : ByteArray
    fun clean()
}