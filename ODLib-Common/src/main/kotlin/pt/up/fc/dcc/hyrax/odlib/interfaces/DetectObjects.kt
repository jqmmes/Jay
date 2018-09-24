package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.ODModel
import pt.up.fc.dcc.hyrax.odlib.ODUtils

interface DetectObjects {
    var minimumScore: Float
    val models: List<ODModel>

    fun extractModel(model: ODModel)
    fun downloadModel(model: ODModel)
    fun checkDownloadedModel(name: String): Boolean

    //fun loadModel(path: String, label: String = String(), score: Float = minimumScore)
    fun loadModel(model: ODModel)
    fun setMinAcceptScore(score: Float)
    fun detectObjects(imgPath: String) : List<ODUtils.ODDetection>
    fun detectObjects(imgData: ByteArray) : List<ODUtils.ODDetection>
    fun getByteArrayFromImage(imgPath: String) : ByteArray
    fun close()
}