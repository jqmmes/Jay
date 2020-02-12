package pt.up.fc.dcc.hyrax.odlib.tensorflow

import android.content.Context
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.structures.Detection
import pt.up.fc.dcc.hyrax.odlib.structures.Model
import java.io.*
import java.util.zip.GZIPInputStream

class DroidTensorflowLite(private val context: Context) : DetectObjects {

    override var minimumScore: Float = 0f

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
        ODLogger.logInfo("INIT", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}"))
        val tis = TarInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(modelFile))))

        var basePath = File(context.cacheDir.path, "Models/").absolutePath
        var entry: TarEntry? = tis.nextEntry
        if (entry != null && entry.isDirectory) {
            ODLogger.logInfo("MAKE_DIR", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}", "MK_DIR=${File(context.cacheDir.path, "Models/${entry.name}").path}"))
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
                ODLogger.logInfo("MAKE_DIR", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}", "MK_DIR=${File(context.cacheDir.path, "Models/${entry.name}").path}"))
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
            ODLogger.logInfo("EXTRACT_FILE", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}", "FILE=${File(context.cacheDir.path, entry.name).path}"))
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
                ODLogger.logWarn("ERROR", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}", "EXTRACT=${File(context.cacheDir.path, entry.name).path}"))
            }

            entry = tis.nextEntry
        }
        ODLogger.logInfo("COMPLETE", actions = *arrayOf("MODEL_FILE=${modelFile.absolutePath}"))
        return basePath
    }

    override fun downloadModel(model: Model): File? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun checkDownloadedModel(name: String): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun loadModel(model: Model, completeCallback: ((ODProto.Status) -> Unit)?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun modelLoaded(model: Model): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setMinAcceptScore(score: Float) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun detectObjects(imgPath: String): List<Detection> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun detectObjects(imgData: ByteArray): List<Detection> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun clean() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}