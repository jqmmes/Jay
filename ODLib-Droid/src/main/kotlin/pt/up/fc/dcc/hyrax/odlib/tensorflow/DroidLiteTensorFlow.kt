package pt.up.fc.dcc.hyrax.odlib.tensorflow

import android.content.Context
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.structures.Detection
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import java.io.File

/**
 * TODO: New DetectObjects interface
 */

class DroidLiteTensorFlow(private val context: Context) : DetectObjects {
    override var minimumScore: Float
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}
    override val models: List<Model>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun extractModel(modelFile: File): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun downloadModel(model: Model): File? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun checkDownloadedModel(name: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun loadModel(model: Model, completeCallback: ((ODProto.Status) -> Unit)?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun modelLoaded(model: Model): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setMinAcceptScore(score: Float) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun detectObjects(imgPath: String): List<Detection> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun detectObjects(imgData: ByteArray): List<Detection> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun clean() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}