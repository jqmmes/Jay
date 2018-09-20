package pt.up.fc.dcc.hyrax.odlib.interfaces

import pt.up.fc.dcc.hyrax.odlib.ODUtils

interface DetectObjects {
    var minimumScore: Float

    fun loadModel(path: String, label: String = String(), score: Float = minimumScore)
    fun setMinAcceptScore(score: Float)
    fun detectObjects(imgPath: String) : List<ODUtils.ODDetection>
    fun detectObjects(imgData: ByteArray) : List<ODUtils.ODDetection>
    fun getByteArrayFromImage(imgPath: String) : ByteArray
    fun close()
}