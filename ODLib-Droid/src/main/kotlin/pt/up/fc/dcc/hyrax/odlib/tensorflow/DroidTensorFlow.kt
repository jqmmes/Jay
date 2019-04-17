package pt.up.fc.dcc.hyrax.odlib.tensorflow

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.structures.Detection
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import pt.up.fc.dcc.hyrax.odlib.utils.ImageUtils
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

@Suppress("unused")
class DroidTensorFlow(private val context: Context) : DetectObjects {
    override fun modelLoaded(model: Model): Boolean {
        if (localDetector == null) return false
        return true
    }

    override var minimumScore: Float = 0f

    private var localDetector : Classifier? = null
    private val tfOdApiInputSize : Int = 300
    private var minimumConfidence : Float = 0.1f
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
                        "ssd_resnet_50_fpn_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_resnet50_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz",
                        checkDownloadedModel("ssd_resnet_50_fpn_coco")
                )
        )

    override fun clean() {
        if (localDetector != null) localDetector!!.close()
        localDetector = null
    }

    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        return ImageUtils.getByteArrayFromImage(imgPath)
    }

    override fun detectObjects(imgData: ByteArray) : List<Detection> {
        return detectObjects(ImageUtils.getBitmapFromByteArray(imgData))
    }

    private fun detectObjects(imgData: Bitmap) : List<Detection> {
        ODLogger.logInfo("DroidTensorFlow, DETECT_OBJECTS, INIT")
        if (localDetector == null) {
            ODLogger.logWarn("DroidTensorFlow, DETECT_OBJECTS, ERROR, NO_MODEL_LOADED")
            return emptyList()
        }
        ODLogger.logInfo("DroidTensorFlow, DETECT_OBJECTS, RECOGNIZE_IMAGE, INIT")
        val results : List<Classifier.Recognition> = localDetector!!.recognizeImage(ImageUtils.scaleImage(imgData, tfOdApiInputSize))
        ODLogger.logInfo("DroidTensorFlow, DETECT_OBJECTS, RECOGNIZE_IMAGE, COMPLETE")
        val mappedRecognitions : MutableList<Detection> = ArrayList()
        for (result : Classifier.Recognition? in results) {
            if (result == null) continue
            if (result.confidence == null) continue
            if (result.confidence >= minimumConfidence) {
                mappedRecognitions.add(Detection(score = result.confidence, class_ = result.title!!.toFloat
                ().toInt()))
            }
        }
        ODLogger.logInfo("DroidTensorFlow, DETECT_OBJECTS, COMPLETE")
        return mappedRecognitions
    }

    private fun loadModel(path: String) { //, score: Float
        ODLogger.logInfo("DroidTensorFlow, LOAD_MODEL, START, MODEL_PATH=$path")
        localDetector = null
        try {
            localDetector = TensorFlowObjectDetectionAPIModel.create(
                    Resources.getSystem().assets, path, tfOdApiInputSize)
            ODLogger.logInfo("DroidTensorFlow, LOAD_MODEL, COMPLETE, MODEL_PATH=$path")
        } catch (e: IOException) {
            ODLogger.logError("DroidTensorFlow, LOAD_MODEL, ERROR, MODEL_PATH=$path")
        }
    }

    override fun loadModel(model: Model, completeCallback: ((ODProto.Status) -> Unit)?) {
        localDetector = null
        thread(name="DroidTensorflow loadModel") {
            ODLogger.logInfo("DroidTensorFlow, LOAD_MODEL, INIT")
            var modelPath = File(context.cacheDir, "Models/${model.modelName}").absolutePath
            ODLogger.logInfo("DroidTensorFlow, LOAD_MODEL, MODEL_PATH=$modelPath")
            if (!checkDownloadedModel(model.modelName)) {
                ODLogger.logInfo("DroidTensorFlow, LOAD_MODEL, NEED_TO_DOWNLOAD_MODEL, INIT")
                val tmpFile = downloadModel(model)
                ODLogger.logInfo("DroidTensorFlow, LOAD_MODEL, NEED_TO_DOWNLOAD_MODEL, COMPLETE")
                if (tmpFile != null) {
                    try {
                        modelPath = extractModel(tmpFile)
                        if (File(modelPath) != File(context.cacheDir, "Models/${model.modelName}")) {
                            File(modelPath).renameTo(File(context.cacheDir, "Models/${model.modelName}"))
                            ODLogger.logInfo("DroidTensorFlow, LOAD_MODEL, RENAME_TO=${File(context.cacheDir, "Models/${model.modelName}").absolutePath}")
                            modelPath = File(context.cacheDir, "Models/${model.modelName}").absolutePath
                        }
                    } catch (e: EOFException) {
                        ODLogger.logError("DroidTensorFlow, LOAD_MODEL, ERROR, BAD_EOF")
                    }
                    tmpFile.delete()
                } else {
                    ODLogger.logError("DroidTensorFlow, LOAD_MODEL, ERROR, DOWNLOAD_FAILED")
                }
            }
            ODLogger.logInfo("DroidTensorFlow, LOAD_MODEL, LOADING_MODEL, INIT")
            loadModel(File(modelPath, "frozen_inference_graph.pb").absolutePath)
            ODLogger.logInfo("DroidTensorFlow, LOAD_MODEL, LOADING_MODEL, COMPLETE")
            completeCallback?.invoke(ODUtils.genStatusSuccess()!!)
        }
    }


    override fun setMinAcceptScore(score: Float) {
        minimumConfidence = score
    }

    override fun detectObjects(imgPath: String) : List<Detection> {
        return detectObjects(BitmapFactory.decodeFile(imgPath))
    }

    override fun checkDownloadedModel(name: String): Boolean {
        val modelCache = File(context.cacheDir, "Models")
        if (!modelCache.exists()) return false
        for (file in modelCache.listFiles())
            if (file.isDirectory && file.name == name) return true
        return false
    }

    override fun downloadModel(model: Model): File? {
        ODLogger.logInfo("DroidTensorFlow, DOWNLOAD_MODEL, MODEL_ID=${model.modelId}, MODEL_NAME=${model.modelName}, " +
                "MODEL_URL=${model.remoteUrl}")
        var count: Int
        if (File(context.cacheDir, "Models").exists() || !File(context.cacheDir, "Models").isDirectory) File(context
                .cacheDir, "Models").mkdirs()
        val tmpFile  = File.createTempFile(model.modelName + "-",".tar.gz",File(context
                .cacheDir, "Models"))
        try {
            val url = URL(model.remoteUrl)

            val connection : URLConnection = url.openConnection()
            connection.connect()

            // this will be useful so that you can show a typical 0-100%
            // progress bar
            val lengthOfFile = connection.contentLength

            // download the file
            val input = BufferedInputStream(url.openStream(),
                    //8192)
                    8192)
            ODLogger.logInfo("DroidTensorFlow, DOWNLOAD_MODEL, MODEL_SIZE=$lengthOfFile")

            // Output stream
            val output = FileOutputStream(tmpFile)

            val data = ByteArray(1024)

            var total: Long = 0

            count = input.read(data)
            while (count != -1) {
                total += count.toLong()
                // publishing the progress....
                // After this onProgressUpdate will be called
                //publishProgress("" + (total * 100 / lengthOfFile).toInt())

                // writing data to file
                output.write(data, 0, count)
                count = input.read(data)
            }

            // flushing output
            output.flush()

            // closing streams
            output.close()
            input.close()

        } catch (e: Exception) {
            ODLogger.logError("DroidTensorFlow, DOWNLOAD_MODEL, ERROR")
        }
        ODLogger.logInfo("DroidTensorFlow, DOWNLOAD_MODEL, COMPLETE")

        return tmpFile
    }

    override fun extractModel(modelFile: File) : String {
        ODLogger.logInfo("DroidTensorFlow, EXTRACT_MODEL, INIT")
        val tis = TarInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(modelFile))))

        var basePath = File(context.cacheDir.path, "Models/").absolutePath
        var entry : TarEntry? = tis.nextEntry
        if (entry!= null && entry.isDirectory) {
            ODLogger.logInfo("DroidTensorFlow, EXTRACT_MODEL, MK_DIR=${File(context.cacheDir.path, "Models/${entry.name}").path}")
            File(context.cacheDir.path, "Models/${entry.name}").mkdirs()
            basePath = File(context.cacheDir.path, "Models/${entry.name}").absolutePath
            entry = tis.nextEntry
        }
        while (entry != null) {
            if (entry.name.contains("PaxHeader")) {
                entry = tis.nextEntry
                continue
            }
            if (entry.isDirectory) {
                ODLogger.logInfo("DroidTensorFlow, EXTRACT_MODEL, MK_DIR=${File(context.cacheDir.path, entry.name).path}")
                File(context.cacheDir.path, "Models/${entry.name}").mkdirs()
                entry = tis.nextEntry
                continue
            }
            if (entry.name.contains("frozen_inference_graph.pb")) {
                basePath = File(context.cacheDir, "Models/${entry.name.substring(0, entry.name.indexOf
                ("frozen_inference_graph.pb"))}").absolutePath
            }
            var count: Int
            val data = ByteArray(2048)
            ODLogger.logInfo("DroidTensorFlow, EXTRACT_MODEL, EXTRACT=${File(context.cacheDir.path, entry.name).path}")
            try {
                File(context.cacheDir.path, "Models/${entry.name}").mkdirs()
                File(context.cacheDir.path, "Models/${entry.name}").delete()
                val fos = FileOutputStream(File(context.cacheDir.path, "Models/${entry.name}"))

                val dest = BufferedOutputStream(fos)

                count = tis.read(data)
                while (count != -1) {
                    dest.write(data, 0, count)
                    count = tis.read(data)
                }

                dest.flush()
                dest.close()
            } catch (ignore : Exception) {
                ODLogger.logInfo("DroidTensorFlow, ERROR, EXTRACT=${File(context.cacheDir.path, entry.name).path}")
            }

            entry = tis.nextEntry
        }
        ODLogger.logInfo("DroidTensorFlow, COMPLETE, PATH=$basePath")
        return basePath
    }
}