/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.tensorflow

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.Graph
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import java.io.IOException
import java.util.*

@Suppress("KDocUnresolvedReference")

///**
//* Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
//* github.com/tensorflow/models/tree/master/research/object_detection
//*/
class TensorFlowObjectDetection : Classifier {

    //Only return this many results.
    private val maxResults: Int = 100

    //Config values.
    private lateinit var inputName: String
    private var inputSize: Int = 0

    private lateinit var intValues: IntArray
    private lateinit var byteValues: ByteArray
    private lateinit var outputLocations : FloatArray
    private lateinit var outputScores : FloatArray
    private lateinit var outputClasses : FloatArray
    private lateinit var outputNumDetections : FloatArray
    private lateinit var outputNames : Array<String>
    private var logStats : Boolean = false

    private lateinit var inferenceInterface: TensorflowInference
    private lateinit var loadedGraph: Graph

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     */
    @Throws(IOException::class)
    override fun init(modelPath: String, inputSize: Int, assetManager: AssetManager?, isQuantized: Boolean?, numThreads: Int?, device: String?) {
        JayLogger.logInfo("INIT")

        this.inferenceInterface = TensorflowInference(assetManager!!, modelPath)

        try {
            this.inferenceInterface = TensorflowInference(assetManager, modelPath)
        } catch (e: RuntimeException) {
            JayLogger.logInfo("ERROR")
        }

        loadedGraph = this.inferenceInterface.graph()

        this.inputName = "image_tensor"
        // The inputName node has a shape of [N, H, W, C], where
        // N is the batch size
        // H = W are the height and width
        //        C is the number of channels (3 for our purposes - RGB)
        loadedGraph.operation(this.inputName)
                ?: throw RuntimeException("Failed to find input Node '" + this.inputName + "'")
        this.inputSize = inputSize
        // The outputScoresName node has a shape of [N, NumLocations], where N
        // is the batch size.
        loadedGraph.operation("detection_scores")
                ?: throw RuntimeException("Failed to find output Node 'detection_scores'")
        loadedGraph.operation("detection_boxes")
                ?: throw RuntimeException("Failed to find output Node 'detection_boxes'")
        loadedGraph.operation("detection_classes")
                ?: throw RuntimeException("Failed to find output Node 'detection_classes'")

        //Pre-allocate buffers.
        this.outputNames = arrayOf("detection_boxes", "detection_scores", "detection_classes", "num_detections")
        this.intValues = IntArray(this.inputSize * this.inputSize)
        this.byteValues = ByteArray(this.inputSize * this.inputSize * 3)
        this.outputScores = FloatArray(maxResults)
        this.outputLocations = FloatArray(maxResults * 4)
        this.outputClasses = FloatArray(maxResults)
        this.outputNumDetections = FloatArray(1)

        JayLogger.logInfo("COMPLETE")
    }

    override fun recognizeImage(bitmap : Bitmap) : List<Classifier.Recognition>{
        JayLogger.logInfo("INIT")
        //Preprocess the image data to extract R, G and B bytes from int of form 0x00RRGGBB
        //on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i: Int in intValues.indices) {
            byteValues[i * 3 + 2] = intValues[i].and(0xFF).toByte()
            byteValues[i * 3 + 1] = (intValues[i].shr(8)).and(0xFF).toByte()
            byteValues[i * 3 + 0] = (intValues[i].shr(16)).and(0xFF).toByte()
        }

        //Copy the input data into TensorFlow.
        JayLogger.logInfo("CREATE_SESSION_INIT")
        val sessionInferenceInterface = TensorflowInference(loadedGraph)
        JayLogger.logInfo("CREATE_SESSION_COMPLETE")
        JayLogger.logInfo("FEED_INTERFACE_INIT")
        sessionInferenceInterface.feed(inputName, byteValues, 1L, inputSize.toLong(), inputSize.toLong(), 3L)
        JayLogger.logInfo("FEED_INTERFACE_COMPLETE")

        //Run the inference call.
        JayLogger.logInfo("RUN_INIT")
        sessionInferenceInterface.run(outputNames, logStats)
        JayLogger.logInfo("RUN_COMPLETE")

        //Copy the output Tensor back into the output array.
        val outputLocations = FloatArray(maxResults * 4)
        val outputScores = FloatArray(maxResults)
        val outputClasses = FloatArray(maxResults)
        val outputNumDetections = FloatArray(1)
        sessionInferenceInterface.fetch(outputNames[0], outputLocations)
        sessionInferenceInterface.fetch(outputNames[1], outputScores)
        sessionInferenceInterface.fetch(outputNames[2], outputClasses)
        sessionInferenceInterface.fetch(outputNames[3], outputNumDetections)

        //Find the best detections.
        val pq: PriorityQueue<Classifier.Recognition> = PriorityQueue(1, { lhs, rhs -> compareValues(rhs.confidence, lhs.confidence) })

        JayLogger.logInfo("CHECK_RESULTS_INIT")
        //Scale them back to the input size.
        for (i: Int in outputScores.indices) {
            val detection =
                    RectF(
                            outputLocations[4 * i + 1] * inputSize,
                            outputLocations[4 * i] * inputSize,
                            outputLocations[4 * i + 3] * inputSize,
                            outputLocations[4 * i + 2] * inputSize)
            pq.add(Classifier.Recognition("" + i, outputClasses[i].toString(), outputScores[i], detection))
            if (outputScores[i] >= 0.3f)
                JayLogger.logInfo("CHECK_RESULTS", actions = arrayOf("RESULT_CLASS=${COCODataLabels.label(outputClasses[i].toInt())}", "RESULT_SCORE=${outputScores[i]}"))
        }

        val recognitions: ArrayList<Classifier.Recognition> = ArrayList()
        for (i: Int in 0..pq.size.coerceAtMost(maxResults)) {
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            recognitions.add(pq.poll())
        }
        JayLogger.logInfo("CHECK_RESULTS_COMPLETE")
        JayLogger.logInfo("CLOSE_SESSION_INIT")
        sessionInferenceInterface.closeSession()
        JayLogger.logInfo("CLOSE_SESSION_COMPLETE")
        JayLogger.logInfo("COMPLETE")
        return recognitions
    }


    override fun close() {
        inferenceInterface.close()
    }
}