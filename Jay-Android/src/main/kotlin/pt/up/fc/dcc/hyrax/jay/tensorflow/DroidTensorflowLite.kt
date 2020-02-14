package pt.up.fc.dcc.hyrax.jay.tensorflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import pt.up.fc.dcc.hyrax.jay.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.protoc.JayProto
import pt.up.fc.dcc.hyrax.jay.structures.Detection
import pt.up.fc.dcc.hyrax.jay.structures.Model
import pt.up.fc.dcc.hyrax.jay.utils.ImageUtils
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

class DroidTensorflowLite(private val context: Context) : DetectObjects {

    override var minimumScore: Float = 0f
    private var localDetector: Classifier? = null
    private val tfOdApiInputSize: Int = 320//500
    private var minimumConfidence: Float = 0.1f

    override val models: List<Model>
        get() = listOf(
                Model(0,
                        "ssd_mobilenet_v3_large_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v3_large_coco_2019_08_14.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v3_large_coco")
                ),
                Model(1,
                        "ssd_mobilenet_v3_small_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v3_small_coco_2019_08_14.tar.gz",
                        checkDownloadedModel("ssd_mobilenet_v3_small_coco")
                )
        )

    override fun extractModel(modelFile: File): String {
        JayLogger.logInfo("INIT", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}"))
        var tis: TarInputStream?
        try {
            tis = TarInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(modelFile))))
        } catch (e: java.lang.Exception) {
            tis = TarInputStream(BufferedInputStream(FileInputStream(modelFile)))
        }
        if (tis == null) throw Error("Error unzipping ${modelFile.absolutePath}")

        var basePath = File(context.cacheDir.path, "Models/").absolutePath
        var entry: TarEntry? = tis.nextEntry
        if (entry != null && entry.isDirectory) {
            JayLogger.logInfo("MAKE_DIR", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}", "MK_DIR=${File(context.cacheDir.path, "Models/${entry.name}").path}"))
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
                JayLogger.logInfo("MAKE_DIR", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}", "MK_DIR=${File(context.cacheDir.path, "Models/${entry.name}").path}"))
                File(context.cacheDir.path, "Models/${entry.name}").mkdirs()
                entry = tis.nextEntry
                continue
            }
            if (entry.name.contains("model.tflite")) {
                basePath = File(context.cacheDir, "Models/${entry.name.substring(0, entry.name.indexOf("model.tflite"))}").absolutePath
            }
            var count: Int
            val data = ByteArray(2048)
            JayLogger.logInfo("EXTRACT_FILE", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}", "FILE=${File(context.cacheDir.path, entry.name).path}"))
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
            } catch (ignore: Exception) {
                JayLogger.logWarn("ERROR", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}", "EXTRACT=${File(context.cacheDir.path, entry.name).path}"))
            }

            entry = tis.nextEntry
        }
        JayLogger.logInfo("COMPLETE", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}"))
        return basePath
    }

    override fun downloadModel(model: Model): File? {
        JayLogger.logInfo("INIT", actions = *arrayOf("MODEL_ID=${model.modelId}", "MODEL_NAME=${model.modelName}", "MODEL_URL=${model.remoteUrl}"))
        var count: Int
        if (File(context.cacheDir, "Models").exists() || !File(context.cacheDir, "Models").isDirectory) File(context.cacheDir, "Models").mkdirs()
        val tmpFile = File.createTempFile(model.modelName + "-", ".tar.gz", File(context.cacheDir, "Models"))
        try {
            val url = URL(model.remoteUrl)

            val connection: URLConnection = url.openConnection()
            connection.connect()

            // this will be useful so that you can show a typical 0-100% progress bar
            val lengthOfFile = connection.contentLength

            // download the file
            val input = BufferedInputStream(url.openStream(), 8192)
            JayLogger.logInfo("MODEL_INFO", actions = *arrayOf("MODEL_ID=${model.modelId}", "MODEL_SIZE=$lengthOfFile"))

            // Output stream
            val output = FileOutputStream(tmpFile)

            val data = ByteArray(1024)

            var total: Long = 0

            count = input.read(data)
            while (count != -1) {
                total += count.toLong()

                output.write(data, 0, count)
                count = input.read(data)
            }

            // flushing output
            output.flush()

            // closing streams
            output.close()
            input.close()

        } catch (e: Exception) {
            JayLogger.logError("ERROR", actions = *arrayOf("MODEL_ID=${model.modelId}"))
        }
        JayLogger.logInfo("COMPLETE", actions = *arrayOf("MODEL_ID=${model.modelId}"))

        return tmpFile
    }

    override fun checkDownloadedModel(name: String): Boolean {
        val modelCache = File(context.cacheDir, "Models")
        if (!modelCache.exists()) return false
        for (file in modelCache.listFiles())
            if (file.isDirectory && file.name == name) return true
        return false
    }


    override fun loadModel(model: Model, completeCallback: ((JayProto.Status) -> Unit)?) {
        localDetector = null
        thread(name = "DroidTensorflowLite loadModel") {
            JayLogger.logInfo("INIT", actions = *arrayOf("MODEL_ID=${model.modelId}"))
            var modelPath = File(context.cacheDir, "Models/${model.modelName}").absolutePath
            JayLogger.logInfo("LOADING_MODEL_INIT", actions = *arrayOf("MODEL_ID=${model.modelId}", "MODEL_PATH=$modelPath"))
            if (!checkDownloadedModel(model.modelName)) {
                JayLogger.logInfo("NEED_TO_DOWNLOAD_MODEL_INIT", actions = *arrayOf("MODEL_ID=${model.modelId}"))
                val tmpFile = downloadModel(model)
                JayLogger.logInfo("NEED_TO_DOWNLOAD_MODEL_COMPLETE", actions = *arrayOf("MODEL_ID=${model.modelId}"))
                if (tmpFile != null) {
                    try {
                        modelPath = extractModel(tmpFile)
                        if (File(modelPath) != File(context.cacheDir, "Models/${model.modelName}")) {
                            File(modelPath).renameTo(File(context.cacheDir, "Models/${model.modelName}"))
                            JayLogger.logInfo("RENAME", actions = *arrayOf("MODEL_ID=${model.modelId}", "NEW_NAME=${File(context.cacheDir, "Models/${model.modelName}").absolutePath}"))
                            modelPath = File(context.cacheDir, "Models/${model.modelName}").absolutePath
                        }
                    } catch (e: EOFException) {
                        JayLogger.logError("ERROR", actions = *arrayOf("MODEL_ID=${model.modelId}", "ERROR_MSG=BAD_EOF"))
                    }
                    tmpFile.delete()
                } else {
                    JayLogger.logError("ERROR", actions = *arrayOf("MODEL_ID=${model.modelId}", "ERROR_MSG=DOWNLOAD_FAILED"))
                }
            }
            JayLogger.logInfo("LOADING_MODEL_INIT", actions = *arrayOf("MODEL_ID=${model.modelId}"))
            loadModel(File(modelPath, "model.tflite").absolutePath)
            JayLogger.logInfo("LOADING_MODEL_COMPLETE", actions = *arrayOf("MODEL_ID=${model.modelId}"))
            completeCallback?.invoke(JayUtils.genStatusSuccess()!!)
        }
    }

    private fun loadModel(modelPath: String) {
        JayLogger.logInfo("INIT", actions = *arrayOf("MODEL_PATH=$modelPath"))
        localDetector = null
        try {
            localDetector = TFLiteInference.create(true, tfOdApiInputSize, "GPU", modelPath, context, 4)
            JayLogger.logInfo("COMPLETE", actions = *arrayOf("MODEL_PATH=$modelPath"))
        } catch (e: IOException) {
            JayLogger.logError("ERROR", actions = *arrayOf("MODEL_PATH=$modelPath"))
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
        return detectObjects(BitmapFactory.decodeFile(imgPath))
    }

    override fun detectObjects(imgData: ByteArray): List<Detection> {
        if (imgData.isNotEmpty()) return detectObjects(ImageUtils.getBitmapFromByteArray(imgData))
        return listOf()
    }

    private fun detectObjects(imgBitmap: Bitmap): List<Detection> {
        JayLogger.logInfo("INIT")
        if (localDetector == null) {
            JayLogger.logWarn("ERROR", actions = *arrayOf("ERROR_MESSAGE=NO_MODEL_LOADED"))
            return emptyList()
        }
        JayLogger.logInfo("RECOGNIZE_IMAGE_INIT")
        val results: List<Classifier.Recognition> = localDetector!!.recognizeImage(ImageUtils.scaleImage(imgBitmap, tfOdApiInputSize))
        JayLogger.logInfo("RECOGNIZE_IMAGE_COMPLETE")
        val mappedRecognitions: MutableList<Detection> = ArrayList()
        for (result: Classifier.Recognition? in results) {
            if (result == null) continue
            if (result.confidence == null) continue
            if (result.confidence >= minimumConfidence) {
                mappedRecognitions.add(Detection(score = result.confidence, class_ = result.title!!.toFloat
                ().toInt()))
            }
        }
        JayLogger.logInfo("COMPLETE")
        return mappedRecognitions
    }

    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        return ImageUtils.getByteArrayFromImage(imgPath)
    }

    override fun clean() {
        localDetector?.close()
        localDetector = null
    }
}