package pt.up.fc.dcc.hyrax.odlib.tensorflow

/* Copyright 2016 The TensorFlow Authors. All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Trace
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.Comparator
import java.util.PriorityQueue
import java.util.StringTokenizer
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import pt.up.fc.dcc.hyrax.odlib.tensorflow.Classifier.Recognition

@Suppress("KDocUnresolvedReference")
/**
 * A detector for general purpose object detection as described in Scalable Object Detection using
 * Deep Neural Networks (https://arxiv.org/abs/1312.2249).
 */
class TensorFlowMultiBoxDetector private constructor() : Classifier {

    // Config values.
    private var inputName: String? = null
    private var inputSize: Int = 0
    private var imageMean: Int = 0
    private var imageStd: Float = 0.toFloat()

    // Pre-allocated buffers.
    private var intValues: IntArray? = null
    private var floatValues: FloatArray? = null
    private var outputLocations: FloatArray? = null
    private var outputScores: FloatArray? = null
    private var outputNames: Array<String>? = null
    private var numLocations: Int = 0

    private var logStats = false

    private var inferenceInterface: TensorFlowInferenceInterface? = null

    private var boxPriors: FloatArray? = null

    override val statString: String
        get() = inferenceInterface!!.statString

    @Throws(IOException::class)
    private fun loadCoderOptions(
            assetManager: AssetManager, locationFilename: String, boxPriors: FloatArray?) {
        // Try to be intelligent about opening from assets or sdcard depending on prefix.
        val assetPrefix = "file:///android_asset/"
        val `is`: InputStream
        `is` = if (locationFilename.startsWith(assetPrefix)) {
            assetManager.open(locationFilename.split(assetPrefix.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1])
        } else {
            FileInputStream(locationFilename)
        }

        // Read values. Number of values per line doesn't matter, as long as they are separated
        // by commas and/or whitespace, and there are exactly numLocations * 8 values total.
        // Values are in the order mean, std for each consecutive corner of each box, for a total of 8
        // per location.
        val reader = BufferedReader(InputStreamReader(`is`))
        var priorIndex = 0
        for (line : String in reader.lines()) {
            val st = StringTokenizer(line, ", ")
            while (st.hasMoreTokens()) {
                val token = st.nextToken()
                try {
                    val number = java.lang.Float.parseFloat(token)
                    boxPriors!![priorIndex++] = number
                } catch (e: NumberFormatException) {
                    // Silently ignore.
                }

            }
        }
        if (priorIndex != boxPriors!!.size) {
            throw RuntimeException(
                    "BoxPrior length mismatch: " + priorIndex + " vs " + boxPriors.size)
        }
    }

    private fun decodeLocationsEncoding(locationEncoding: FloatArray): FloatArray {
        val locations = FloatArray(locationEncoding.size)
        var nonZero = false
        for (i in 0 until numLocations) {
            for (j in 0..3) {
                val currEncoding = locationEncoding[4 * i + j]
                nonZero = nonZero || currEncoding != 0.0f

                val mean = boxPriors!![i * 8 + j * 2]
                val stdDev = boxPriors!![i * 8 + j * 2 + 1]
                var currentLocation = currEncoding * stdDev + mean
                currentLocation = Math.max(currentLocation, 0.0f)
                currentLocation = Math.min(currentLocation, 1.0f)
                locations[4 * i + j] = currentLocation
            }
        }

        return locations
    }

    private fun decodeScoresEncoding(scoresEncoding: FloatArray): FloatArray {
        val scores = FloatArray(scoresEncoding.size)
        for (i in scoresEncoding.indices) {
            scores[i] = 1 / (1 + Math.exp((-scoresEncoding[i]).toDouble())).toFloat()
        }
        return scores
    }

    override fun recognizeImage(bitmap: Bitmap): List<Recognition> {
        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage")

        Trace.beginSection("preprocessBitmap")
        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i in intValues!!.indices) {
            floatValues!![i * 3 + 0] = ((intValues!![i] shr 16 and 0xFF) - imageMean) / imageStd
            floatValues!![i * 3 + 1] = ((intValues!![i] shr 8 and 0xFF) - imageMean) / imageStd
            floatValues!![i * 3 + 2] = ((intValues!![i] and 0xFF) - imageMean) / imageStd
        }
        Trace.endSection() // preprocessBitmap

        // Copy the input data into TensorFlow.
        Trace.beginSection("feed")
        inferenceInterface!!.feed(inputName, floatValues, 1L, inputSize.toLong(), inputSize.toLong(), 3L)
        Trace.endSection()

        // Run the inference call.
        Trace.beginSection("run")
        inferenceInterface!!.run(outputNames, logStats)
        Trace.endSection()

        // Copy the output Tensor back into the output array.
        Trace.beginSection("fetch")
        val outputScoresEncoding = FloatArray(numLocations)
        val outputLocationsEncoding = FloatArray(numLocations * 4)
        inferenceInterface!!.fetch(outputNames!![0], outputLocationsEncoding)
        inferenceInterface!!.fetch(outputNames!![1], outputScoresEncoding)
        Trace.endSection()

        outputLocations = decodeLocationsEncoding(outputLocationsEncoding)
        outputScores = decodeScoresEncoding(outputScoresEncoding)

        // Find the best detections.
        val pq = PriorityQueue(
                1,
                Comparator<Recognition> { lhs, rhs ->
                    // Intentionally reversed to put high confidence at the head of the queue.
                    java.lang.Float.compare(rhs.confidence!!, lhs.confidence!!)
                })

        // Scale them back to the input size.
        for (i in outputScores!!.indices) {
            val detection = RectF(
                    outputLocations!![4 * i] * inputSize,
                    outputLocations!![4 * i + 1] * inputSize,
                    outputLocations!![4 * i + 2] * inputSize,
                    outputLocations!![4 * i + 3] * inputSize)
            pq.add(Recognition("" + i, null, outputScores!![i], detection))
        }

        val recognitions = ArrayList<Recognition>()
        for (i in 0 until Math.min(pq.size, MAX_RESULTS)) {
            recognitions.add(pq.poll())
        }
        Trace.endSection() // "recognizeImage"
        return recognitions
    }

    override fun enableStatLogging(debug: Boolean) {
        this.logStats = debug
    }

    override fun close() {
        inferenceInterface!!.close()
    }

    companion object {
        // Only return this many results.
        private const val MAX_RESULTS = Integer.MAX_VALUE

        /**
         * Initializes a native TensorFlow session for classifying images.
         *
         * @param assetManager The asset manager to be used to load assets.
         * @param modelFilename The filepath of the model GraphDef protocol buffer.
         * @param locationFilename The filepath of label file for classes.
         * @param inputSize The input size. A square image of inputSize x inputSize is assumed.
         * @param imageMean The assumed mean of the image values.
         * @param imageStd The assumed std of the image values.
         * @param inputName The label of the image input node.
         * @param outputName The label of the output node.
         */
        fun create(
                assetManager: AssetManager,
                modelFilename: String,
                locationFilename: String,
                imageMean: Int,
                imageStd: Float,
                inputName: String,
                outputLocationsName: String,
                outputScoresName: String): Classifier {
            val d = TensorFlowMultiBoxDetector()

            d.inferenceInterface = TensorFlowInferenceInterface(assetManager, modelFilename)

            val g = d.inferenceInterface!!.graph()

            d.inputName = inputName
            // The inputName node has a shape of [N, H, W, C], where
            // N is the batch size
            // H = W are the height and width
            // C is the number of channels (3 for our purposes - RGB)
            val inputOp = g.operation(inputName) ?: throw RuntimeException("Failed to find input Node '$inputName'")
            d.inputSize = inputOp.output<Any>(0).shape().size(1).toInt()
            d.imageMean = imageMean
            d.imageStd = imageStd
            // The outputScoresName node has a shape of [N, NumLocations], where N
            // is the batch size.
            val outputOp = g.operation(outputScoresName)
                    ?: throw RuntimeException("Failed to find output Node '$outputScoresName'")
            d.numLocations = outputOp.output<Any>(0).shape().size(1).toInt()

            d.boxPriors = FloatArray(d.numLocations * 8)

            try {
                d.loadCoderOptions(assetManager, locationFilename, d.boxPriors)
            } catch (e: IOException) {
                throw RuntimeException("Error initializing box priors from $locationFilename")
            }

            // Pre-allocate buffers.
            d.outputNames = arrayOf(outputLocationsName, outputScoresName)
            d.intValues = IntArray(d.inputSize * d.inputSize)
            d.floatValues = FloatArray(d.inputSize * d.inputSize * 3)
            d.outputScores = FloatArray(d.numLocations)
            d.outputLocations = FloatArray(d.numLocations * 4)

            return d
        }
    }
}
