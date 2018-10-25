package pt.up.fc.dcc.hyrax.odlib.tensorflow

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import pt.up.fc.dcc.hyrax.odlib.utils.ODUtils
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.utils.ImageUtils
import java.io.*
import java.util.zip.GZIPInputStream
import java.net.URL
import java.net.URLConnection
import kotlin.concurrent.thread

@Suppress("unused")
class DroidTensorFlow(private val context: Context) : DetectObjects {
    override var minimumScore: Float = 0f

    private var localDetector : Classifier? = null
    private val tfOdApiInputSize : Int = 300
    private var minimumConfidence : Float = 0.1f
    override val models: List<ODModel>
        get() = listOf(
                ODModel(0,
                        "ssd_mobilenet_v1_fpn_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v1_fpn_coco")
                ),
                ODModel(1,
                        "ssd_mobilenet_v1_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v1_coco_2018_01_28.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v1_coco")
                ),
                ODModel(2,
                        "ssd_mobilenet_v2_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v2_coco_2018_03_29.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v2_coco")
                ),
                ODModel(3,
                        "ssdlite_mobilenet_v2_coco",
                        "http://download.tensorflow.org/models/object_detection/ssdlite_mobilenet_v2_coco_2018_05_09.tar.gz",
                        checkDownloadedModel("ssdlite_mobilenet_v2_coco")
                ),
                ODModel(4,
                        "ssd_resnet_50_fpn_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_resnet50_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz",
                        checkDownloadedModel("ssd_resnet_50_fpn_coco")
                )
        )

    override fun close() {
        if (localDetector != null) localDetector!!.close()
    }

    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        return ImageUtils.getByteArrayFromImage(imgPath)
    }

    override fun detectObjects(imgData: ByteArray) : List<ODUtils.ODDetection> {
        return detectObjects(ImageUtils.getBitmapFromByteArray(imgData))
    }

    private fun detectObjects(imgData: Bitmap) : List<ODUtils.ODDetection> {
        if (localDetector == null) {
            ODLogger.logWarn("No model has been loaded yet")
            return emptyList()
        }
        ODLogger.logInfo("DroidTensorFlow detecting objects...")
        val results : List<Classifier.Recognition> = localDetector!!.recognizeImage(ImageUtils.scaleImage(imgData, tfOdApiInputSize))
        val mappedRecognitions : MutableList<ODUtils.ODDetection> = ArrayList()
        for (result : Classifier.Recognition? in results) {
            if (result == null) continue
            if (result.confidence == null) continue
            if (result.confidence >= minimumConfidence) {
                mappedRecognitions.add(ODUtils.ODDetection(score = result.confidence, class_ = result.title!!.toFloat
                ().toInt()))
            }
        }
        ODLogger.logInfo("DroidTensorFlow detecting objects... Complete")
        return mappedRecognitions
    }

    private fun loadModel(path: String) { //, score: Float
        ODLogger.logInfo("Loading Model from: $path")
        try {
            localDetector = TensorFlowObjectDetectionAPIModel.create(
                    Resources.getSystem().assets, path, tfOdApiInputSize)
            ODLogger.logInfo("Model loaded successfully")
        } catch (e: IOException) {
            ODLogger.logError("Error loading model")
        }

    }

    override fun loadModel(model: ODModel) {
        thread(name="DroidTensorflow loadModel") {
            var modelPath = File(context.cacheDir, "Models/${model.modelName}").absolutePath
            println("${model.modelName}\t${model.remoteUrl}")
            if (!checkDownloadedModel(model.modelName)) {
                val tmpFile = downloadModel(model)
                if (tmpFile != null) {
                    modelPath = extractModel(tmpFile)
                    if (File(modelPath) != File(context.cacheDir, "Models/${model.modelName}")) {
                        File(modelPath).renameTo(File(context.cacheDir, "Models/${model.modelName}"))
                        ODLogger.logInfo("Renaming model dir to: ${File(context.cacheDir, "Models/${model.modelName}")
                                .absolutePath}")
                        modelPath = File(context.cacheDir, "Models/${model.modelName}").absolutePath
                    }
                    tmpFile.delete()
                }
                else ODLogger.logError("model Download Failed")
            }
            loadModel(File(modelPath, "frozen_inference_graph.pb").absolutePath)
        }
    }


    override fun setMinAcceptScore(score: Float) {
        minimumConfidence = score
    }

    override fun detectObjects(imgPath: String) : List<ODUtils.ODDetection> {
        return detectObjects(BitmapFactory.decodeFile(imgPath))
    }

    override fun checkDownloadedModel(name: String): Boolean {
        val modelCache = File(context.cacheDir, "Models")
        if (!modelCache.exists()) return false
        for (file in modelCache.listFiles())
            if (file.isDirectory && file.name == name) return true
        return false
    }

    override fun downloadModel(model: ODModel): File? {
        ODLogger.logInfo("Downloading model: ${model.modelName}")
        var count: Int
        if (File(context.cacheDir, "Models").exists() || !File(context.cacheDir, "Models").isDirectory) File(context
                .cacheDir, "Models").mkdirs()
        val tmpFile  = File.createTempFile(model.modelName + "-",".tar.gz",File(context
                .cacheDir, "Models"))
        try {
            val url = URL(model.remoteUrl)
            ODLogger.logInfo("downloading from: $url\t${model.remoteUrl}")
            val connection : URLConnection = url.openConnection()
            connection.connect()

            // this will be useful so that you can show a tipical 0-100%
            // progress bar
            val lengthOfFile = connection.contentLength

            // download the file
            val input = BufferedInputStream(url.openStream(),
                    //8192)
                    8192)
            ODLogger.logInfo("total size: $lengthOfFile")

            // Output stream
            val output = FileOutputStream(tmpFile)

            val data = ByteArray(1024)

            var total: Long = 0

            count = input.read(data)
            while (count != -1) {
                total += count.toLong()
                // publishing the progress....
                // After this onProgressUpdate will be called
                //publishProgress("" + (total * 100 / lenghtOfFile).toInt())

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
            ODLogger.logError(e.message.toString())
        }
        ODLogger.logInfo("Download Complete")

        return tmpFile
    }

    override fun extractModel(modelFile: File) : String {
        ODLogger.logInfo("extracting model: ${modelFile.path}")
        val tis = TarInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(modelFile))))

        var basePath = File(context.cacheDir.path, "Models/").absolutePath
        var entry : TarEntry? = tis.nextEntry
        if (entry!= null && entry.isDirectory) {
            ODLogger.logInfo("mkdir\t${File(context.cacheDir.path, "Models/${entry.name}").path}")
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
                ODLogger.logInfo("mkdir\t${File(context.cacheDir.path, entry.name).path}")
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
            ODLogger.logInfo("extract\t${File(context.cacheDir.path, entry.name).path}")
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
                ODLogger.logWarn("Failed extract of ${File(context.cacheDir.path, entry.name).path}")
            }

            entry = tis.nextEntry
        }
        return basePath
    }
}