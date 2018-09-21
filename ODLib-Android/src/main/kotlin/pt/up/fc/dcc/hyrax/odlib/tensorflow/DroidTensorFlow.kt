package pt.up.fc.dcc.hyrax.odlib.tensorflow

import pt.up.fc.dcc.hyrax.odlib.ODUtils
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import java.io.IOException
import android.content.res.Resources
import android.graphics.*
import java.util.LinkedList
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream


internal class DroidTensorFlow : DetectObjects {
    override var minimumScore: Float = 0f

    private lateinit var localDetector : Classifier
    private val TF_OD_API_INPUT_SIZE : Long = 300L
    //private val TF_OD_API_MODEL_FILE = "file:///android_asset/ssd_mobilenet_v1_android_export.pb"
    //private val TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt"
    private var minimumConfidence : Float = 0.0f


    override fun close() {
        localDetector.close()
    }

    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        val stream = ByteArrayOutputStream()
        BitmapFactory.decodeFile(imgPath).compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    override fun detectObjects(imgData: ByteArray) : List<ODUtils.ODDetection> {
        //Verificar como funciona o decodeByteArray
        return detectObjects(BitmapFactory.decodeByteArray(imgData, 10, 10))
    }

    fun detectObjects(imgData: Bitmap) : List<ODUtils.ODDetection> {
        val results : List<Classifier.Recognition> = localDetector.recognizeImage(imgData)
        val mappedRecognitions : MutableList<ODUtils.ODDetection> =  LinkedList()
        for (result : Classifier.Recognition in results) {
            if (result.confidence!! >= minimumConfidence) {
                mappedRecognitions.add(ODUtils.ODDetection(score = result.confidence, class_ = result.title!!.toInt(), box = ODUtils.Box()))
            }
        }
        return mappedRecognitions
    }

    override fun loadModel(path: String, label: String, score: Float) {
        try {
            localDetector = TensorFlowObjectDetectionAPIModel.create(
                    Resources.getSystem().assets, path, label, TF_OD_API_INPUT_SIZE)
            //cropSize = TF_OD_API_INPUT_SIZE
        } catch (e: IOException) {
            //LOGGER.e("Exception initializing classifier!", e)
            //val toast = Toast.makeText(
            //        getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT)
            //toast.show()
            //finish()
        }

    }

    override fun setMinAcceptScore(score: Float) {
        minimumConfidence = score
    }

    override fun detectObjects(imgPath: String) : List<ODUtils.ODDetection> {
        return detectObjects(BitmapFactory.decodeFile(imgPath))
    }
}