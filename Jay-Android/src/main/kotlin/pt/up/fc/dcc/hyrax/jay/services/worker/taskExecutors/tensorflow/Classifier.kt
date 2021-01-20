/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.tensorflow

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Generic interface for interacting with different recognition engines.
 */
interface Classifier {

    class Recognition(private val id: String?, val title: String?, val confidence: Float?, private var location: RectF?) {
        override fun toString(): String {
            var resultString = ""
            if (id != null) resultString += "[$id] "
            if (title != null) resultString += "$title "
            if (confidence != null) resultString += String.format("(%.1f%%) ", confidence * 100.0f)
            if (location != null) resultString += "[${location?.left},${location?.top},${location?.right},${location?.bottom}]"
            return resultString.trim { it <= ' ' }
        }
    }

    fun init(modelPath: String, inputSize: Int, assetManager: AssetManager? = null, isQuantized: Boolean? = null, numThreads: Int? = null, device: String? = null)
    fun recognizeImage(bitmap: Bitmap): List<Recognition>
    fun close()
}
