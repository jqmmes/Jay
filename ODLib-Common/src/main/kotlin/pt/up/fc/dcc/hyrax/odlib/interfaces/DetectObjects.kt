package pt.up.fc.dcc.hyrax.odlib.interfaces

interface DetectObjects {
    var minimumScore: Float

    fun setModel(path: String, label: String = String(), score: Float = minimumScore)
    fun setLabels(label: String)
    fun setScore(score: Float)
    fun detectObjects(imgPath: String)
}