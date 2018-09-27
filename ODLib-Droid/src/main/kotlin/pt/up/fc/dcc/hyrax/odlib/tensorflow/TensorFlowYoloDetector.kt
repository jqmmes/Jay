/* Copyright 2017 The TensorFlow Authors. All Rights Reserved.
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
import java.util.ArrayList
import java.util.Comparator
import java.util.PriorityQueue
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import pt.up.fc.dcc.hyrax.odlib.tensorflow.Classifier
import pt.up.fc.dcc.hyrax.odlib.tensorflow.Classifier.Recognition

/** An object detector that uses TF and a YOLO model to detect objects.  */
class TensorFlowYoloDetector private constructor() : Classifier {

    // Config values.
    private var inputName: String? = null
    private var inputSize: Int = 0

    // Pre-allocated buffers.
    private var intValues: IntArray? = null
    private var floatValues: FloatArray? = null
    private var outputNames: Array<String>? = null

    private var blockSize: Int = 0

    private var logStats = false

    private var inferenceInterface: TensorFlowInferenceInterface? = null

    override val statString: String
        get() = inferenceInterface!!.statString

    private fun expit(x: Float): Float {
        return (1.0 / (1.0 + Math.exp((-x).toDouble()))).toFloat()
    }

    private fun softmax(vals: FloatArray) {
        var max = java.lang.Float.NEGATIVE_INFINITY
        for (`val` in vals) {
            max = Math.max(max, `val`)
        }
        var sum = 0.0f
        for (i in vals.indices) {
            vals[i] = Math.exp((vals[i] - max).toDouble()).toFloat()
            sum += vals[i]
        }
        for (i in vals.indices) {
            vals[i] = vals[i] / sum
        }
    }

    override fun recognizeImage(bitmap: Bitmap): List<Recognition> {
        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage")

        Trace.beginSection("preprocessBitmap")
        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i in intValues!!.indices) {
            floatValues!![i * 3 + 0] = (intValues!![i] shr 16 and 0xFF) / 255.0f
            floatValues!![i * 3 + 1] = (intValues!![i] shr 8 and 0xFF) / 255.0f
            floatValues!![i * 3 + 2] = (intValues!![i] and 0xFF) / 255.0f
        }
        Trace.endSection() // preprocessBitmap

        // Copy the input data into TensorFlow.
        Trace.beginSection("feed")
        inferenceInterface!!.feed(inputName, floatValues, 1, inputSize.toLong(), inputSize.toLong(), 3)
        Trace.endSection()

        // Run the inference call.
        Trace.beginSection("run")
        inferenceInterface!!.run(outputNames, logStats)
        Trace.endSection()

        // Copy the output Tensor back into the output array.
        Trace.beginSection("fetch")
        val gridWidth = bitmap.width / blockSize
        val gridHeight = bitmap.height / blockSize
        val output = FloatArray(gridWidth * gridHeight * (NUM_CLASSES + 5) * NUM_BOXES_PER_BLOCK)
        inferenceInterface!!.fetch(outputNames!![0], output)
        Trace.endSection()

        // Find the best detections.
        val pq = PriorityQueue(
                1,
                Comparator<Recognition> { lhs, rhs ->
                    // Intentionally reversed to put high confidence at the head of the queue.
                    java.lang.Float.compare(rhs.confidence!!, lhs.confidence!!)
                })

        for (y in 0 until gridHeight) {
            for (x in 0 until gridWidth) {
                for (b in 0 until NUM_BOXES_PER_BLOCK) {
                    val offset = (gridWidth * (NUM_BOXES_PER_BLOCK * (NUM_CLASSES + 5)) * y
                            + NUM_BOXES_PER_BLOCK * (NUM_CLASSES + 5) * x
                            + (NUM_CLASSES + 5) * b)

                    val xPos = (x + expit(output[offset + 0])) * blockSize
                    val yPos = (y + expit(output[offset + 1])) * blockSize

                    val w = (Math.exp(output[offset + 2].toDouble()) * ANCHORS[2 * b + 0]).toFloat() * blockSize
                    val h = (Math.exp(output[offset + 3].toDouble()) * ANCHORS[2 * b + 1]).toFloat() * blockSize

                    val rect = RectF(
                            Math.max(0f, xPos - w / 2),
                            Math.max(0f, yPos - h / 2),
                            Math.min((bitmap.width - 1).toFloat(), xPos + w / 2),
                            Math.min((bitmap.height - 1).toFloat(), yPos + h / 2))
                    val confidence = expit(output[offset + 4])

                    var detectedClass = -1
                    var maxClass = 0f

                    val classes = FloatArray(NUM_CLASSES)
                    for (c in 0 until NUM_CLASSES) {
                        classes[c] = output[offset + 5 + c]
                    }
                    softmax(classes)

                    for (c in 0 until NUM_CLASSES) {
                        if (classes[c] > maxClass) {
                            detectedClass = c
                            maxClass = classes[c]
                        }
                    }

                    val confidenceInClass = maxClass * confidence
                    if (confidenceInClass > 0.01) {
                        pq.add(Recognition("" + offset, LABELS[detectedClass], confidenceInClass, rect))
                    }
                }
            }
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
        // Only return this many results with at least this confidence.
        private const val MAX_RESULTS = 5

        private const val NUM_CLASSES = 20

        private const val NUM_BOXES_PER_BLOCK = 5

        private val ANCHORS = doubleArrayOf(1.08, 1.19, 3.42, 4.41, 6.63, 11.38, 9.42, 5.11, 16.62, 10.52)

        private val LABELS = arrayOf("aeroplane", "bicycle", "bird", "boat", "bottle", "bus", "car", "cat", "chair", "cow", "diningtable", "dog", "horse", "motorbike", "person", "pottedplant", "sheep", "sofa", "train", "tvmonitor")

        /** Initializes a native TensorFlow session for classifying images.  */
        fun create(
                assetManager: AssetManager,
                modelFilename: String,
                inputSize: Int,
                inputName: String,
                outputName: String,
                blockSize: Int): Classifier {
            val d = TensorFlowYoloDetector()
            d.inputName = inputName
            d.inputSize = inputSize

            // Pre-allocate buffers.
            d.outputNames = outputName.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            d.intValues = IntArray(inputSize * inputSize)
            d.floatValues = FloatArray(inputSize * inputSize * 3)
            d.blockSize = blockSize

            d.inferenceInterface = TensorFlowInferenceInterface(assetManager, modelFilename)

            return d
        }
    }
}