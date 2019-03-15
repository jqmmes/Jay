/* Copyright 2016 The TensorFlow Authors. All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License")
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package pt.up.fc.dcc.hyrax.odlib.tensorflow

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.Graph
//import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import java.io.IOException
import java.util.*

@Suppress("KDocUnresolvedReference")

///**
//* Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
//* github.com/tensorflow/models/tree/master/research/object_detection
//*/
class TensorFlowObjectDetectionAPIModel private constructor() : Classifier {
    override val statString: String
        get() = inferenceInterface.statString

    override fun enableStatLogging(debug: Boolean) {
        this.logStats = debug
    }

    //Only return this many results.
    private val maxResults : Int = 100

    //Config values.
    private lateinit var inputName : String
    private var inputSize : Int = 0

    private lateinit var intValues : IntArray
    private lateinit var byteValues : ByteArray
    private lateinit var outputLocations : FloatArray
    private lateinit var outputScores : FloatArray
    private lateinit var outputClasses : FloatArray
    private lateinit var outputNumDetections : FloatArray
    private lateinit var outputNames : Array<String>
    private var logStats : Boolean = false

    private lateinit var inferenceInterface : MyTensorFlowInferenceInterface

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     */
    companion object {
        private const val MAX_RESULTS = 100
        private lateinit var loadedGraph : Graph

        @Throws(IOException::class)
        fun create (
                assetManager : AssetManager,
                modelFilename : String,
                inputSize : Int) : Classifier {
            val d = TensorFlowObjectDetectionAPIModel()

            /*val labelsInput: InputStream?
            val actualFilename : String = labelFilename.split("file:///android_asset/")[1]
            labelsInput = assetManager.open(actualFilename)
            val br: BufferedReader?
            br = BufferedReader(InputStreamReader(labelsInput))
            var line : String?
            line = br.readLine()
            while (line != null) {
                d.labels.add(line)
                line = br.readLine()
            }
            br.clean()*/


            d.inferenceInterface = MyTensorFlowInferenceInterface(assetManager, modelFilename)

            loadedGraph = d.inferenceInterface.graph()

            //TensorFlowInferenceInterface(loadedGraph) //create new session

            d.inputName = "image_tensor"
            // The inputName node has a shape of [N, H, W, C], where
            // N is the batch size
            // H = W are the height and width
            //        C is the number of channels (3 for our purposes - RGB)
            loadedGraph.operation(d.inputName) ?: throw RuntimeException("Failed to find input Node '" + d.inputName + "'")
            d.inputSize = inputSize
            // The outputScoresName node has a shape of [N, NumLocations], where N
            // is the batch size.
            loadedGraph.operation("detection_scores") ?: throw RuntimeException("Failed to find output Node 'detection_scores'")
            loadedGraph.operation("detection_boxes") ?: throw RuntimeException("Failed to find output Node 'detection_boxes'")
            loadedGraph.operation("detection_classes") ?: throw RuntimeException("Failed to find output Node 'detection_classes'")

            //Pre-allocate buffers.
            d.outputNames = arrayOf("detection_boxes", "detection_scores",
                    "detection_classes", "num_detections")
            d.intValues = IntArray(d.inputSize * d.inputSize)
            d.byteValues = ByteArray(d.inputSize * d.inputSize * 3)
            d.outputScores = FloatArray(MAX_RESULTS)
            d.outputLocations = FloatArray(MAX_RESULTS * 4)
            d.outputClasses = FloatArray(MAX_RESULTS)
            d.outputNumDetections = FloatArray(1)
            return d
        }


        //
    }

    //TensorFlowObjectDetectionAPIModel()


    override fun recognizeImage(bitmap : Bitmap) : List<Classifier.Recognition>{
        // Log this method so that it can be analyzed with systrace.
        //Trace.beginSection("recognizeImage")

        //Trace.beginSection("preprocessBitmap")
        //Preprocess the image data to extract R, G and B bytes from int of form 0x00RRGGBB
        //on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i : Int  in 0..(intValues.size-1)) {
            byteValues[i * 3 + 2] = intValues[i].and(0xFF).toByte()
            byteValues[i * 3 + 1] = (intValues[i].shr(8)).and(0xFF).toByte()
            byteValues[i * 3 + 0] = (intValues[i].shr(16)).and(0xFF).toByte()
        }
        //Trace.endSection() //preprocessBitmap

        //Copy the input data into TensorFlow.
        //Trace.beginSection("feed")
        //inputName: String!, src: ByteArray!, vararg dims: Long
        ODLogger.logInfo("TensorflowObjectDetectionAPIModel creating new session...")
        val sessionInferenceInterface = MyTensorFlowInferenceInterface(loadedGraph)
        ODLogger.logInfo("TensorflowObjectDetectionAPIModel session created")
        ODLogger.logInfo("TensorflowObjectDetectionAPIModel feeding interface...")
        sessionInferenceInterface.feed(inputName, byteValues, 1L, inputSize.toLong(), inputSize.toLong(), 3L)
        ODLogger.logInfo("TensorflowObjectDetectionAPIModel interface fed...")
        //inferenceInterface.feed(inputName, byteValues, 1L, inputSize, inputSize, 3L)
        //Trace.endSection()

        //Run the inference call.
        //Trace.beginSection("run")
        ODLogger.logInfo("TensorflowObjectDetectionAPIModel running...")
        sessionInferenceInterface.run(outputNames, logStats)
        ODLogger.logInfo("TensorflowObjectDetectionAPIModel complete")
        //inferenceInterface.run(outputNames, logStats)
        //Trace.endSection()

        //Copy the output Tensor back into the output array.
        //Trace.beginSection("fetch")
        val outputLocations = FloatArray(maxResults * 4)
        val outputScores = FloatArray(maxResults)
        val outputClasses = FloatArray(maxResults)
        val outputNumDetections = FloatArray(1)
        /*inferenceInterface.fetch(outputNames[0], outputLocations)
        inferenceInterface.fetch(outputNames[1], outputScores)
        inferenceInterface.fetch(outputNames[2], outputClasses)
        inferenceInterface.fetch(outputNames[3], outputNumDetections)*/
        sessionInferenceInterface.fetch(outputNames[0], outputLocations)
        sessionInferenceInterface.fetch(outputNames[1], outputScores)
        sessionInferenceInterface.fetch(outputNames[2], outputClasses)
        sessionInferenceInterface.fetch(outputNames[3], outputNumDetections)
        //Trace.endSection()



        //Find the best detections.
        val  pq : PriorityQueue<Classifier.Recognition> =
                PriorityQueue(
                        1, kotlin.Comparator<Classifier.Recognition> { lhs, rhs -> compareValues(rhs.confidence, lhs.confidence) })

        ODLogger.logInfo("TensorflowObjectDetectionAPIModel Checking results...")
        //Scale them back to the input size.
        for (i : Int in 0..(outputScores.size-1)) {
            val detection =
                    RectF(
                            outputLocations[4 * i + 1] * inputSize,
                            outputLocations[4 * i] * inputSize,
                            outputLocations[4 * i + 3] * inputSize,
                            outputLocations[4 * i + 2] * inputSize)
            pq.add(Classifier.Recognition("" + i, outputClasses[i].toString(), outputScores[i], detection))
            if (outputScores[i] >= 0.3f)
                ODLogger.logInfo("Detection #$i\n\t\tClass: ${COCODataLabels.label(outputClasses[i].toInt())}\n\t\tScore: " +
                        "${outputScores[i]}")
        }

        val recognitions : ArrayList<Classifier.Recognition> = ArrayList()
        for (i : Int in 0..Math.min(pq.size, maxResults)) {
            recognitions.add(pq.poll())
        }
        //Trace.endSection() //"recognizeImage"
        ODLogger.logInfo("TensorflowObjectDetectionAPIModel closing session...")
        //sessionInferenceInterface.clean()
        sessionInferenceInterface.closeSession()
        ODLogger.logInfo("TensorflowObjectDetectionAPIModel closing session... Closed")
        return recognitions
    }


    override fun close() {
        inferenceInterface.close()
    }
}