/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.droid_jay_app.interfaces

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
