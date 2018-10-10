package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.io.File

interface DetectObjects {
    var minimumScore: Float
    val models: List<ODModel>

    fun extractModel(modelFile: File) : String
    fun downloadModel(model: ODModel): File?
    fun checkDownloadedModel(name: String): Boolean

    //fun loadModel(path: String, label: String = String(), score: Float = minimumScore)
    fun loadModel(model: ODModel)
    fun setMinAcceptScore(score: Float)
    fun detectObjects(imgPath: String) : List<ODUtils.ODDetection>
    fun detectObjects(imgData: ByteArray) : List<ODUtils.ODDetection>
    fun getByteArrayFromImage(imgPath: String) : ByteArray
    fun close()
}