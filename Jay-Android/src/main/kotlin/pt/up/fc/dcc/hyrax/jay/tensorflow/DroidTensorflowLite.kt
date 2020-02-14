package pt.up.fc.dcc.hyrax.jay.tensorflow

import android.content.Context
import android.graphics.BitmapFactory
import pt.up.fc.dcc.hyrax.jay.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.structures.Detection
import pt.up.fc.dcc.hyrax.jay.structures.Model
import pt.up.fc.dcc.hyrax.jay.utils.ImageUtils
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import pt.up.fc.dcc.hyrax.jay.utils.TFUtils
import java.io.File
import kotlin.concurrent.thread

class DroidTensorflowLite(private val context: Context) : DetectObjects {

    override var minimumScore: Float = 0f
    private var localDetector: Classifier? = null
    private val tfOdApiInputSize: Int = 320//500
    private var minimumConfidence: Float = 0.1f

    override val models: List<Model>
        get() = listOf(
                Model(0,
                        "ssd_mobilenet_v3_large_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v3_large_coco_2019_08_14.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v3_large_coco")
                ),
                Model(1,
                        "ssd_mobilenet_v3_small_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v3_small_coco_2019_08_14.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v3_small_coco")
                )
        )

    override fun extractModel(modelFile: File): String {
        return TFUtils.extractModel(context, modelFile, "model.tflite")
    }

    override fun downloadModel(model: Model): File? {
        return TFUtils.downloadModel(context, model)
    }

    override fun checkDownloadedModel(name: String): Boolean {
        return TFUtils.checkDownloadedModel(context, name)
    }

    override fun loadModel(model: Model, completeCallback: ((JayProto.Status) -> Unit)?) {
        localDetector = null
        thread(name = "DroidTensorflowLite loadModel") {
            localDetector = TFUtils.loadModel(TFLiteInference(), context, model, "model.tflite", tfOdApiInputSize, isQuantized = true, numThreads = 4, device = "CPU")
            completeCallback?.invoke(JayUtils.genStatusSuccess()!!)
        }
    }

    override fun modelLoaded(model: Model): Boolean {
        if (localDetector == null) return false
        return true
    }

    override fun setMinAcceptScore(score: Float) {
        minimumConfidence = score
    }

    override fun detectObjects(imgPath: String): List<Detection> {
        return TFUtils.detectObjects(localDetector, minimumConfidence, tfOdApiInputSize, BitmapFactory.decodeFile(imgPath))
    }

    override fun detectObjects(imgData: ByteArray): List<Detection> {
        if (imgData.isNotEmpty()) return TFUtils.detectObjects(localDetector, minimumConfidence, tfOdApiInputSize, ImageUtils.getBitmapFromByteArray(imgData))
        return listOf()
    }


    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        return ImageUtils.getByteArrayFromImage(imgPath)
    }

    override fun clean() {
        localDetector?.close()
        localDetector = null
    }
}