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
import android.graphics.BitmapFactory
import pt.up.fc.dcc.hyrax.droid_jay_app.interfaces.Classifier
import pt.up.fc.dcc.hyrax.droid_jay_app.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.droid_jay_app.utils.ImageUtils
import pt.up.fc.dcc.hyrax.droid_jay_app.utils.TFUtils
import java.io.File
import kotlin.concurrent.thread

class DroidTensorflowLite(private val context: Context) :
    DetectObjects {

    override var minimumScore: Float = 0f
    override var useGPU = false
    override var useNNAPI = false

    private var localDetector: Classifier? = null
    private var tfOdApiInputSize: Int = 320//500
    private var minimumConfidence: Float = 0.1f

    override val models: List<Model>
        get() = listOf(
                // http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v3_large_coco_2019_08_14.tar.gz
                Model(0,
                        "ssd_mobilenet_v3_large_coco",
                        "https://www.dropbox.com/s/8z71cy95karsnhf/ssd_mobilenet_v3_large_coco_2019_08_14.tar.gz?dl=1",
                        checkDownloadedModel("ssd_mobilenet_v3_large_coco"),
                        false,
                        inputSize = 640
                ),
                // "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v3_small_coco_2019_08_14.tar.gz",
                Model(1,
                        "ssd_mobilenet_v3_small_coco",
                        "https://www.dropbox.com/s/7jb2jyx7h467hjd/ssd_mobilenet_v3_small_coco_2019_08_14.tar.gz?dl=1",
                        checkDownloadedModel("ssd_mobilenet_v3_small_coco"),
                        false,
                        inputSize = 640
                ),
                Model(2,
                        "ssd_mobilenet_v1_fpn_coco",
                        "https://www.dropbox.com/s/pphefxdmn3ryzk8/ssd_mobilenet_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz?dl=1",
                        checkDownloadedModel("ssd_mobilenet_v1_fpn_coco"),
                        false,
                        640
                ),
                Model(3,
                        "ssd_resnet50_v1_fpn_coco",
                        "https://www.dropbox.com/s/2i9vfpjpdyejwp3/ssd_resnet50_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz?dl=1",
                        checkDownloadedModel("ssd_resnet50_v1_fpn_coco"),
                        isQuantized = false,
                        inputSize = 640
                ),
                Model(4,
                        "ssd_mobilenet_v3_quantized_large_coco",
                        "https://www.dropbox.com/s/j2kewwsapjvi5qu/ssd_mobilenet_v3_quantized_large_coco_2019_08_14.tar.gz?dl=1",
                        checkDownloadedModel("ssd_mobilenet_v3_quantized_large_coco"),
                        true,
                        inputSize = 320
                )
        )

    override fun extractModel(modelFile: File): String {
        return TFUtils.extractModel(context, modelFile, "model.tflite", true)
    }

    override fun downloadModel(model: Model): File? {
        return TFUtils.downloadModel(context, model, true)
    }

    override fun checkDownloadedModel(name: String): Boolean {
        return TFUtils.checkDownloadedModel(context, name, true)
    }

    override fun loadModel(model: Model, completeCallback: ((Boolean) -> Unit)?) {
        localDetector = null
        thread(name = "DroidTensorflowLite loadModel") {
            localDetector = TFUtils.loadModel(TFLiteInference(), context, model, "model.tflite", model.inputSize, isQuantized = model.isQuantized, numThreads = 4, device = if (this.useNNAPI) "NNAPI" else if (this.useGPU) "GPU" else "CPU", lite = true)
            this.tfOdApiInputSize = model.inputSize
            completeCallback?.invoke(true)
        }
    }

    override fun modelLoaded(model: Model): Boolean {
        if (localDetector == null) return false
        return true
    }

    override fun setMinAcceptScore(score: Float) {
        minimumConfidence = score
    }

    override fun detectObjects(imgPath: String): List<Detection> {
        return TFUtils.detectObjects(localDetector, minimumConfidence, tfOdApiInputSize, BitmapFactory.decodeFile(imgPath))
    }

    override fun detectObjects(imgData: ByteArray): List<Detection> {
        if (imgData.isNotEmpty()) return TFUtils.detectObjects(localDetector, minimumConfidence, tfOdApiInputSize, ImageUtils.getBitmapFromByteArray(imgData))
        return listOf()
    }


    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        return ImageUtils.getByteArrayFromImage(imgPath)
    }

    override fun clean() {
        localDetector?.close()
        localDetector = null
    }
}