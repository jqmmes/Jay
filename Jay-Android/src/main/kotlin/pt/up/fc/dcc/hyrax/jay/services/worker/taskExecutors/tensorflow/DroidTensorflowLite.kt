package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.tensorflow

import android.content.Context
import android.graphics.BitmapFactory
import pt.up.fc.dcc.hyrax.jay.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.structures.Detection
import pt.up.fc.dcc.hyrax.jay.structures.Model
import pt.up.fc.dcc.hyrax.jay.utils.ImageUtils
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import pt.up.fc.dcc.hyrax.jay.utils.TFUtils
import java.io.File
import kotlin.concurrent.thread

class DroidTensorflowLite(private val context: Context) : DetectObjects {

    override var minimumScore: Float = 0f
    override var useGPU = false
    override var useNNAPI = false

    private var localDetector: Classifier? = null
    private var tfOdApiInputSize: Int = 320//500
    private var minimumConfidence: Float = 0.1f

    override val models: List<Model>
        get() = listOf(
                // http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v3_large_coco_2019_08_14.tar.gz
                Model(0,
                        "ssd_mobilenet_v3_large_coco",
                        "https://www.dropbox.com/s/8z71cy95karsnhf/ssd_mobilenet_v3_large_coco_2019_08_14.tar.gz?dl=1",
                        checkDownloadedModel("ssd_mobilenet_v3_large_coco"),
                        false,
                        320
                ),
                // "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v3_small_coco_2019_08_14.tar.gz",
                Model(1,
                        "ssd_mobilenet_v3_small_coco",
                        "https://www.dropbox.com/s/7jb2jyx7h467hjd/ssd_mobilenet_v3_small_coco_2019_08_14.tar.gz?dl=1",
                        checkDownloadedModel("ssd_mobilenet_v3_small_coco"),
                        false,
                        320
                ),
                Model(2,
                        "ssd_mobilenet_v1_fpn_coco",
                        "https://www.dropbox.com/s/pphefxdmn3ryzk8/ssd_mobilenet_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz?dl=1",
                        checkDownloadedModel("ssd_mobilenet_v1_fpn_coco"),
                        false,
                        640
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
            localDetector = TFUtils.loadModel(TFLiteInference(), context, model, "model.tflite", model.inputSize, isQuantized = model.isQuantized, numThreads = 4, device = if (this.useNNAPI) "NNAPI" else if (this.useGPU) "GPU" else "CPU")
            this.tfOdApiInputSize = model.inputSize
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