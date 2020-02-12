package pt.up.fc.dcc.hyrax.odlib.tensorflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import pt.up.fc.dcc.hyrax.odlib.tensorflow.Classifier.Recognition
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.util.*


/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * github.com/tensorflow/models/tree/master/research/object_detection
 */
class TFLiteInference : Classifier {
    private var isModelQuantized = false

    // Config values.
    private var inputSize = 0
    private val labels = Vector<String>()
    private lateinit var intValues: IntArray
    private lateinit var outputLocations: Array<Array<FloatArray>>
    private lateinit var outputClasses: Array<FloatArray>
    private lateinit var outputScores: Array<FloatArray>
    private lateinit var numDetections: FloatArray
    private var imgData: ByteBuffer? = null
    private var tfLite: Interpreter? = null

    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var tfLiteModel: MappedByteBuffer? = null

    override fun recognizeImage(bitmap: Bitmap): ArrayList<Recognition> {
        /**
         * LOAD IMAGE DATA
         */
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        imgData!!.rewind()
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                if (isModelQuantized) { // Quantized model
                    imgData!!.put((pixelValue shr 16 and 0xFF).toByte())
                    imgData!!.put((pixelValue shr 8 and 0xFF).toByte())
                    imgData!!.put((pixelValue and 0xFF).toByte())
                } else { // Float model
                    imgData!!.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData!!.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData!!.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }

        /**
         * PREPARE OUTPUTS MAP
         */
        outputLocations = Array(1) { Array(NUM_DETECTIONS) { FloatArray(4) } }
        outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
        outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
        numDetections = FloatArray(1)
        val inputArray = arrayOf<Any?>(imgData)
        val outputMap: MutableMap<Int, Any> = HashMap()
        outputMap[0] = outputLocations
        outputMap[1] = outputClasses
        outputMap[2] = outputScores
        outputMap[3] = numDetections

        /**
         * Run Inference
         */
        tfLite!!.runForMultipleInputsOutputs(inputArray, outputMap)

        /**
         * PROCESS OUTPUTS
         */
        val recognitions = ArrayList<Recognition>(NUM_DETECTIONS)
        for (i in 0 until NUM_DETECTIONS) {
            val detection = RectF(
                    outputLocations[0][i][1] * inputSize,
                    outputLocations[0][i][0] * inputSize,
                    outputLocations[0][i][3] * inputSize,
                    outputLocations[0][i][2] * inputSize
            )
            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes+1,
            // while outputClasses correspond to class index from 0 to number_of_classes
            val labelOffset = 1
            recognitions.add(Recognition("" + i, labels[outputClasses[0][i].toInt() + labelOffset], outputScores[0][i], detection))
        }
        return recognitions
    }

    override fun close() {
        tfLite?.close()
        tfLite = null

        gpuDelegate?.close()
        gpuDelegate = null

        nnApiDelegate?.close()
        nnApiDelegate = null

        tfLiteModel = null
    }

    fun loadModel(device: String, numThreads: Int, modelPath: String, context: Context) {
        this.tfLiteModel = FileUtil.loadMappedFile(context, modelPath)
        val tfLiteOptions = Interpreter.Options()
        when (device) {
            "NNAPI" -> {
                val nnApiDelegate = NnApiDelegate()
                tfLiteOptions.addDelegate(nnApiDelegate)
            }
            "GPU" -> {
                val gpuDelegate = GpuDelegate()
                tfLiteOptions.addDelegate(gpuDelegate)
            }
            "CPU" -> {
            }
        }
        tfLiteOptions.setNumThreads(numThreads)
        tfLite = Interpreter(tfLiteModel!!, tfLiteOptions)
    }

    companion object {

        // Constant Values
        private const val NUM_DETECTIONS = 10 // Only return this many results.
        private const val IMAGE_MEAN = 128.0f // Float model
        private const val IMAGE_STD = 128.0f // Float model
        private const val NUM_THREADS = 4 // Number of threads in the java app

        fun create(isQuantized: Boolean, inputSize: Int, device: String, modelPath: String, context: Context, numThreads: Int = NUM_THREADS): Classifier? {

            val inferenceInterface = TFLiteInference()

            inferenceInterface.inputSize = inputSize
            inferenceInterface.isModelQuantized = isQuantized

            // Pre-allocate buffers.
            val numBytesPerChannel: Int = if (isQuantized) {
                1 // Quantized
            } else {
                4 // Floating point
            }

            inferenceInterface.imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * numBytesPerChannel)
            inferenceInterface.imgData!!.order(ByteOrder.nativeOrder())
            inferenceInterface.intValues = IntArray(inputSize * inputSize)
            inferenceInterface.outputLocations = Array(1) { Array(NUM_DETECTIONS) { FloatArray(4) } }
            inferenceInterface.outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
            inferenceInterface.outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
            inferenceInterface.numDetections = FloatArray(1)

            inferenceInterface.loadModel(device, numThreads, modelPath, context)

            return inferenceInterface
        }
    }
}
