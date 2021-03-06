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

package pt.up.fc.dcc.hyrax.droid_jay_app.tensorfow_task.tensorflow

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import pt.up.fc.dcc.hyrax.droid_jay_app.interfaces.Classifier
import pt.up.fc.dcc.hyrax.droid_jay_app.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.droid_jay_app.utils.ImageUtils
import pt.up.fc.dcc.hyrax.droid_jay_app.utils.TFUtils
import java.io.File
import kotlin.concurrent.thread

@Suppress("unused")
class DroidTensorflow(private val context: Context) : DetectObjects {
    override fun modelLoaded(model: Model): Boolean {
        if (localDetector == null) return false
        return true
    }

    override var minimumScore: Float = 0f
    override var useGPU = false
    override var useNNAPI = false

    private var localDetector: Classifier? = null
    private val tfOdApiInputSize: Int = 500
    private var minimumConfidence: Float = 0.1f

    override val models: List<Model>
        get() = listOf(
                Model(0,
                        "ssd_mobilenet_v1_fpn_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v1_fpn_coco")
                ),
                Model(1,
                        "ssd_mobilenet_v1_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v1_coco_2018_01_28.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v1_coco")
                ),
                Model(2,
                        "ssd_mobilenet_v2_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v2_coco_2018_03_29.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v2_coco")
                ),
                Model(3,
                        "ssdlite_mobilenet_v2_coco",
                        "http://download.tensorflow.org/models/object_detection/ssdlite_mobilenet_v2_coco_2018_05_09.tar.gz",
                        checkDownloadedModel("ssdlite_mobilenet_v2_coco")
                ),
                Model(4,
                        "ssd_resnet50_v1_fpn_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_resnet50_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz",
                        checkDownloadedModel("ssd_resnet50_v1_fpn_coco")
                ),
                Model(5,
                        "ssd_mobilenet_v3_large_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v3_large_coco_2019_08_14.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v3_large_coco")
                ),
                Model(6,
                        "ssd_mobilenet_v3_small_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v3_small_coco_2019_08_14.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v3_small_coco")
                )
        )

    override fun clean() {
        if (localDetector != null) localDetector!!.close()
        localDetector = null
    }

    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        return ImageUtils.getByteArrayFromImage(imgPath)
    }

    override fun detectObjects(imgPath: String): List<Detection> {
        return TFUtils.detectObjects(localDetector, minimumConfidence, tfOdApiInputSize, BitmapFactory.decodeFile(imgPath))
    }

    override fun detectObjects(imgData: ByteArray): List<Detection> {
        if (imgData.isNotEmpty()) return TFUtils.detectObjects(localDetector, minimumConfidence, tfOdApiInputSize, ImageUtils.getBitmapFromByteArray(imgData))
        return listOf()
    }

    override fun loadModel(model: Model, completeCallback: ((Boolean) -> Unit)?) {
        localDetector = null
        thread(name = "DroidTensorflow loadModel") {
            localDetector = TFUtils.loadModel(TensorFlowObjectDetection(), context, model, "frozen_inference_graph.pb", tfOdApiInputSize, assetManager = Resources.getSystem().assets, lite = false)
            completeCallback?.invoke(true)
        }
    }


    override fun setMinAcceptScore(score: Float) {
        minimumConfidence = score
    }

    override fun checkDownloadedModel(name: String): Boolean {
        return TFUtils.checkDownloadedModel(context, name, false)
    }

    override fun downloadModel(model: Model): File? {
        return TFUtils.downloadModel(context, model, false)
    }

    override fun extractModel(modelFile: File) : String {
        return TFUtils.extractModel(context, modelFile, "frozen_inference_graph.pb", false)
    }
}