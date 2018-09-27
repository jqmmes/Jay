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
import android.os.Trace
import org.tensorflow.Graph
import org.tensorflow.Operation
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
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
    private val MAX_RESULTS : Int = 100

    //Config values.
    private lateinit var inputName : String
    private var inputSize : Long = 0

    //Pre-allocated buffers.
    private var labels : Vector<String> = Vector()
    private lateinit var intValues : IntArray
    private lateinit var byteValues : ByteArray
    private lateinit var outputLocations : FloatArray
    private lateinit var outputScores : FloatArray
    private lateinit var outputClasses : FloatArray
    private lateinit var outputNumDetections : FloatArray
    private lateinit var outputNames : Array<String>
    private var logStats : Boolean = false

    private lateinit var inferenceInterface : TensorFlowInferenceInterface

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     */
    companion object {
        private const val MAX_RESULTS = 100

        @Throws(IOException::class)
        fun create (
                assetManager : AssetManager,
                modelFilename : String,
                labelFilename : String,
                inputSize : Long) : Classifier {
            val d = TensorFlowObjectDetectionAPIModel()

            val labelsInput: InputStream?
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
            br.close()


            d.inferenceInterface = TensorFlowInferenceInterface(assetManager, modelFilename)

            val g : Graph = d.inferenceInterface.graph()

            d.inputName = "image_tensor"
            // The inputName node has a shape of [N, H, W, C], where
            // N is the batch size
            // H = W are the height and width
            //        C is the number of channels (3 for our purposes - RGB)
            val inputOp : Operation? = g.operation(d.inputName)
            if (inputOp == null) {
                throw RuntimeException("Failed to find input Node '" + d.inputName + "'")
            }
            d.inputSize = inputSize
            // The outputScoresName node has a shape of [N, NumLocations], where N
            // is the batch size.
            val outputOp1 : Operation? = g.operation("detection_scores")
            if (outputOp1 == null) {
                throw RuntimeException("Failed to find output Node 'detection_scores'")
            }
            val outputOp2 : Operation? = g.operation("detection_boxes")
            if (outputOp2 == null) {
                throw RuntimeException("Failed to find output Node 'detection_boxes'")
            }
            val outputOp3 : Operation? = g.operation("detection_classes")
            if (outputOp3 == null) {
                throw RuntimeException("Failed to find output Node 'detection_classes'")
            }

            //Pre-allocate buffers.
            d.outputNames = arrayOf("detection_boxes", "detection_scores",
                    "detection_classes", "num_detections")
            d.intValues = IntArray(d.inputSize.toInt() * d.inputSize.toInt())
            d.byteValues = ByteArray(d.inputSize.toInt() * d.inputSize.toInt() * 3)
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
        Trace.beginSection("recognizeImage")

        Trace.beginSection("preprocessBitmap")
        //Preprocess the image data to extract R, G and B bytes from int of form 0x00RRGGBB
        //on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight())

        for (i : Int  in 0..intValues.size) {
            byteValues[i * 3 + 2] = intValues[i].and(0xFF).toByte()
            byteValues[i * 3 + 1] = (intValues[i].shr(8)).and(0xFF).toByte()
            byteValues[i * 3 + 0] = (intValues[i].shr(16)).and(0xFF).toByte()
        }
        Trace.endSection() //preprocessBitmap

        //Copy the input data into TensorFlow.
        Trace.beginSection("feed")
        //inputName: String!, src: ByteArray!, vararg dims: Long
        inferenceInterface.feed(inputName, byteValues, 1L, inputSize, inputSize, 3L)
        Trace.endSection()

        //Run the inference call.
        Trace.beginSection("run")
        inferenceInterface.run(outputNames, logStats)
        Trace.endSection()

        //Copy the output Tensor back into the output array.
        Trace.beginSection("fetch")
        val outputLocations = FloatArray(MAX_RESULTS * 4)
        val outputScores = FloatArray(MAX_RESULTS)
        val outputClasses = FloatArray(MAX_RESULTS)
        val outputNumDetections = FloatArray(1)
        inferenceInterface.fetch(outputNames[0], outputLocations)
        inferenceInterface.fetch(outputNames[1], outputScores)
        inferenceInterface.fetch(outputNames[2], outputClasses)
        inferenceInterface.fetch(outputNames[3], outputNumDetections)
        Trace.endSection()



        //Find the best detections.
        val  pq : PriorityQueue<Classifier.Recognition> =
                PriorityQueue<Classifier.Recognition>(
                        1, kotlin.Comparator<Classifier.Recognition> { lhs, rhs -> compareValues(rhs.confidence, lhs.confidence) })

        //Scale them back to the input size.
        for (i : Int in 0..outputScores.size) {
            val detection =
                    RectF(
                            outputLocations[4 * i + 1] * inputSize,
                            outputLocations[4 * i] * inputSize,
                            outputLocations[4 * i + 3] * inputSize,
                            outputLocations[4 * i + 2] * inputSize)
            pq.add(Classifier.Recognition("" + i, labels.get(outputClasses[i].toInt()), outputScores[i], detection))
        }

        val recognitions : ArrayList<Classifier.Recognition> = ArrayList<Classifier.Recognition>()
        for (i : Int in 0..Math.min(pq.size, MAX_RESULTS)) {
            recognitions.add(pq.poll())
        }
        Trace.endSection() //"recognizeImage"
        return recognitions
    }


    override fun close() {
        inferenceInterface.close()
    }
}