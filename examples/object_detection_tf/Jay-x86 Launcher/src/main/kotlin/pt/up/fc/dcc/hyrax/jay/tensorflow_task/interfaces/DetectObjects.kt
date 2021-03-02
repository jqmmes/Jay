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

package pt.up.fc.dcc.hyrax.jay.tensorflow_task.interfaces

import pt.up.fc.dcc.hyrax.jay.tensorflow_task.tensorflow.Detection
import pt.up.fc.dcc.hyrax.jay.tensorflow_task.tensorflow.Model
import java.io.File

interface DetectObjects {
    var useNNAPI: Boolean
    var minimumScore: Float
    val models: List<Model>
    var useGPU: Boolean

    fun extractModel(modelFile: File) : String
    fun downloadModel(model: Model): File?
    fun checkDownloadedModel(name: String): Boolean

    fun loadModel(model: Model, completeCallback: ((Boolean) -> Unit)? = null)
    fun modelLoaded(model: Model): Boolean
    fun setMinAcceptScore(score: Float)
    fun detectObjects(imgPath: String) : List<Detection>
    fun detectObjects(imgData: ByteArray) : List<Detection>
    fun getByteArrayFromImage(imgPath: String) : ByteArray
    fun clean()
}