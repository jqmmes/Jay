package pt.up.fc.dcc.hyrax.odlib.tensorflow

import pt.up.fc.dcc.hyrax.odlib.ODUtils
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects

internal class DroidTensorFlow : DetectObjects {
    override fun close() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun detectObjects(imgData: ByteArray) : List<ODUtils.ODDetection> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var minimumScore: Float
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(_) {}

    override fun loadModel(path: String, label: String, score: Float) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setMinAcceptScore(score: Float) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun detectObjects(imgPath: String) : List<ODUtils.ODDetection> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}