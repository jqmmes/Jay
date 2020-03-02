package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.tensorflow

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.tensorflow.Classifier.Recognition
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*


/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * github.com/tensorflow/models/tree/master/research/object_detection
 */
class TFLiteInference : Classifier {

    // Constant Values
    private val numDetections = 10 //2034 // 10 // Only return this many results.
    private val imageMean = 128.0f // Float model
    private val imageStd = 128.0f // Float model

    // Config values.
    private var isModelQuantized = false
    private var inputSize = 0
    private lateinit var intValues: IntArray
    private lateinit var outputLocations: Array<Array<FloatArray>>
    private lateinit var outputClasses: Array<FloatArray>
    private lateinit var outputScores: Array<FloatArray>
    private var imgData: ByteBuffer? = null
    private var tfLite: Interpreter? = null

    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var tfLiteModel: MappedByteBuffer? = null

    override fun init(modelPath: String, inputSize: Int, assetManager: AssetManager?, isQuantized: Boolean?, numThreads: Int?, device: String?) {
        this.inputSize = inputSize
        this.isModelQuantized = isQuantized ?: false

        // Pre-allocate buffers.
        val numBytesPerChannel: Int = if (isQuantized == true) {
            1
        } else {
            4
        }

        this.imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * numBytesPerChannel)
        this.imgData!!.order(ByteOrder.nativeOrder())
        this.intValues = IntArray(inputSize * inputSize)
        this.outputLocations = Array(1) { Array(this.numDetections) { FloatArray(4) } }
        this.outputClasses = Array(1) { FloatArray(this.numDetections) } //4072 // Expected Shape: [1,2034,1,4]
        this.outputScores = Array(1) { FloatArray(this.numDetections) }

        loadModel(device!!, numThreads ?: 4, modelPath)
    }

    override fun recognizeImage(bitmap: Bitmap): ArrayList<Recognition> {
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
                    imgData!!.putFloat(((pixelValue shr 16 and 0xFF) - imageMean) / imageStd)
                    imgData!!.putFloat(((pixelValue shr 8 and 0xFF) - imageMean) / imageStd)
                    imgData!!.putFloat(((pixelValue and 0xFF) - imageMean) / imageStd)
                }
            }
        }

        // PREPARE OUTPUTS MAP
        val inputArray = arrayOf<Any?>(imgData)
        val outputMap: MutableMap<Int, Any> = HashMap()
        outputMap[0] = this.outputLocations
        outputMap[1] = this.outputClasses
        outputMap[2] = this.outputScores
        outputMap[3] = FloatArray(1) // numDetections


        // Run Inference
        tfLite!!.runForMultipleInputsOutputs(inputArray, outputMap)

        /**
         * The results are stored in outputMap[0] per class [detections_0: [class_0, class_1, .., class_91], detections_1: ...]
         * and in outputMap[1] are the locations [detections_0: [[x,y,up,down]], detections_1: ...]
         * Current TFLite models available on ModelZoo are broken and need to be recompiled locally using latest Object Detection API!
         */
        val recognitions = ArrayList<Recognition>(numDetections)
        for (i in 0 until numDetections) {
            val detection = RectF(
                    outputLocations[0][i][1] * inputSize,
                    outputLocations[0][i][0] * inputSize,
                    outputLocations[0][i][3] * inputSize,
                    outputLocations[0][i][2] * inputSize
            )
            /** SSD Mobilenet V1 Model assumes class 0 is background class
             * in label file and class labels start from 1 to number_of_classes+1,
             * while outputClasses correspond to class index from 0 to number_of_classes */
            val labelOffset = 1
            recognitions.add(Recognition("" + i, COCODataLabels.label(outputClasses[0][i].toInt() + labelOffset), outputScores[0][i], detection))
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

    private fun loadMappedFile(filePath: String): MappedByteBuffer? {
        val fd = File(filePath)

        val inputStream = FileInputStream(filePath) //FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = 0L //fileDescriptor.startOffset
        val declaredLength = fd.length() //fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadModel(device: String, numThreads: Int, modelPath: String) {
        this.tfLiteModel = loadMappedFile(modelPath)
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
}
