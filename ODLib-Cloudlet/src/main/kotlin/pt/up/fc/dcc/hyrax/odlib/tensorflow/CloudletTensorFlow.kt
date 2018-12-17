package pt.up.fc.dcc.hyrax.odlib.tensorflow

import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import org.tensorflow.SavedModelBundle
import org.tensorflow.Tensor
import org.tensorflow.types.UInt8
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.utils.ODDetection
import pt.up.fc.dcc.hyrax.odlib.utils.ODLogger
import pt.up.fc.dcc.hyrax.odlib.utils.ODModel
import java.awt.Image
import java.awt.Image.SCALE_FAST
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.*
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.*
import java.util.zip.GZIPInputStream
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.max




/**
 * Java inference for the Object Detection API at:
 * https://github.com/tensorflow/models/blob/master/research/object_detection/
 */
internal class CloudletTensorFlow : DetectObjects {
    override var minimumScore: Float = 0.3f

    private var loadedModel: SavedModelBundle? = null
    private var modelClosed = true
    private var modelCacheDir: String = "/tmp/ODLib/Models/"

    override fun modelLoaded(model: ODModel): Boolean {
        if (modelClosed || loadedModel == null) return false
        return true
    }

    override fun clean() {
        if (loadedModel != null) loadedModel!!.close()
        loadedModel = null
        modelClosed = true
    }


    private fun loadModel(path: String, score: Float = minimumScore) {
        clean()
        loadedModel = SavedModelBundle.load(path, "serve")
        val tensor = Tensor.create(UInt8::class.java, longArrayOf(1L, 1L, 1L, 3), ByteBuffer.wrap(ByteArray(3)))
        loadedModel!!.session().runner()
                .feed("image_tensor", tensor)
                .fetch("detection_scores")
                .fetch("detection_classes")
                .fetch("detection_boxes")
                .run()
        modelClosed = false
        if (score != minimumScore) setMinAcceptScore(score)
    }

    override fun setMinAcceptScore(score: Float) {
        if (score in 0.0f..1.0f) minimumScore = score
    }

    override fun detectObjects(imgData: ByteArray): List<ODDetection> {
        if (loadedModel == null || modelClosed) {
            throw (Exception("\"Model not loaded.\""))
        }
        try {
            return processOutputs(loadedModel!!, makeImageTensor(imgData))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }

    override fun detectObjects(imgPath: String): List<ODDetection> {
        if (loadedModel == null || modelClosed) {
            throw (Exception("\"Model not loaded.\""))
        }
        return processOutputs(loadedModel!!, makeImageTensor(imgPath))
    }

    private fun resizeImage(imgData: ByteArray, maxSize: Int = 300): Image {
        val image = ImageIO.read(ByteArrayInputStream(imgData))
        if (image.width == maxSize && image.height <= maxSize ||
                image.width <= maxSize && image.height == maxSize) return image
        ODLogger.logInfo("Resizing Image...")
        val scale = maxSize.toFloat() / max(image.width, image.height)
        return image.getScaledInstance(floor(image.width * scale).toInt(), floor(image.height * scale).toInt(),
                SCALE_FAST)
    }

    private fun processOutputs(model: SavedModelBundle, tensor: Tensor<UInt8>): List<ODDetection> {
        ODLogger.logInfo("processOutputs\tRUNNING")
        var outputs: List<Tensor<*>>? = null
        tensor.use { input ->
            outputs = model
                    .session()
                    .runner()
                    .feed("image_tensor", input)
                    .fetch("detection_scores")
                    .fetch("detection_classes")
                    .fetch("detection_boxes")
                    .run()
        }
        ODLogger.logInfo("processOutputs\tDONE")

        val returnList: MutableList<ODDetection> = LinkedList()

        outputs!![0].expect(Float::class.javaObjectType).use { scoresT ->
            outputs!![1].expect(Float::class.javaObjectType).use { classesT ->
                outputs!![2].expect(Float::class.javaObjectType).use { _ ->
                    //boxesT ->
                    // All these tensors have:
                    // - 1 as the first dimension
                    // - maxObjects as the second dimension
                    // While boxesT will have 4 as the third dimension (2 sets of (x, y) coordinates).
                    // This can be verified by looking at scoresT.shape() etc.
                    val maxObjects = scoresT.shape()[1].toInt()
                    val scores = scoresT.copyTo(Array(1) { FloatArray(maxObjects) })[0]
                    val classes = classesT.copyTo(Array(1) { FloatArray(maxObjects) })[0]
                    //val boxes = boxesT.copyTo(Array(1) { Array(maxObjects) { FloatArray(4) } })[0]
                    for (i in scores.indices) {
                        if (scores[i] < minimumScore) {
                            continue
                        }
                        returnList.add(ODDetection(scores[i], classes[i].toInt()))
                    }
                }
            }
        }
        return returnList
    }

    private fun bgr2rgb(data: ByteArray) {
        var i = 0
        while (i < data.size) {
            val tmp = data[i]
            data[i] = data[i + 2]
            data[i + 2] = tmp
            i += 3
        }
    }

    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        val img = ImageIO.read(File(imgPath))
        val output = ByteArrayOutputStream()
        ImageIO.write(img, "jpg", output)
        return output.toByteArray()
    }

    private fun toBufferedImage(img: Image): BufferedImage {
        if (img is BufferedImage) {
            return img
        }

        // Create a buffered image with transparency
        val bimage = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_3BYTE_BGR)

        // Draw the image on to the buffered image
        val bGr = bimage.createGraphics()
        bGr.drawImage(img, 0, 0, null)
        bGr.dispose()

        // Return the buffered image
        return bimage
    }

    private fun makeImageTensor(imageData: ByteArray): Tensor<UInt8> {
        ODLogger.logInfo("Making image tensor")
        //val img = convertBufferedImageType(ImageIO.read(ByteArrayInputStream(imageData)))
        //val img = ImageIO.read(ByteArrayInputStream(imageData))

        val img: BufferedImage = toBufferedImage(resizeImage(imageData))
        /*if (img.type != BufferedImage.TYPE_3BYTE_BGR) {
            throw IOException("Expected 3-byte BGR encoding in BufferedImage, found ${img.type}. This code could be made more robust")
        }*/
        val data = (img.raster.dataBuffer as DataBufferByte).data
        // ImageIO.read seems to produce BGR-encoded images, but the model expects RGB.
        bgr2rgb(data)
        val batchSize: Long = 1
        val channels: Long = 3
        val shape = longArrayOf(batchSize, img.height.toLong(), img.width.toLong(), channels)
        val tensor = Tensor.create(UInt8::class.java, shape, ByteBuffer.wrap(data))
        ODLogger.logInfo("Making image tensor\tCOMPLETE")
        return tensor
    }

    @Throws(IOException::class)
    private fun makeImageTensor(filename: String): Tensor<UInt8> {
        ODLogger.logInfo("Making image tensor")
        val img = ImageIO.read(File(filename))
        if (img.type != BufferedImage.TYPE_3BYTE_BGR) {
            throw IOException(
                    String.format(
                            "Expected 3-byte BGR encoding in BufferedImage, found %d (file: %s). This code could be made more robust",
                            img.type, filename))
        }
        val data = (img.data.dataBuffer as DataBufferByte).data
        // ImageIO.read seems to produce BGR-encoded images, but the model expects RGB.
        bgr2rgb(data)
        val batchSize: Long = 1
        val channels: Long = 3
        val shape = longArrayOf(batchSize, img.height.toLong(), img.width.toLong(), channels)
        return Tensor.create(UInt8::class.java, shape, ByteBuffer.wrap(data))
    }

    @Suppress("unused")
    private fun convertBufferedImageType(bufferedImage: BufferedImage): BufferedImage {
        ODLogger.logInfo("Checking image format...")
        val converted = BufferedImage(300, 300, BufferedImage.TYPE_3BYTE_BGR)
        if (bufferedImage.type == BufferedImage.TYPE_4BYTE_ABGR) {
            ODLogger.logInfo("Converting image to correct type (YPE_3BYTE_BGR)")
            for (y in 0 until bufferedImage.height) {
                for (x in 0 until bufferedImage.width) {
                    val argb = bufferedImage.getRGB(x, y)
                    if (argb and 0x00FFFFFF == 0x00FFFFFF) { //if the pixel is transparent
                        converted.setRGB(x, y, -0x1) // white color.
                    } else {
                        converted.setRGB(x, y, argb)
                    }
                }
            }
        }
        ODLogger.logInfo("Checking image format... done")
        return converted
    }

    override fun checkDownloadedModel(name: String): Boolean {
        ODLogger.logInfo("Checking if model has been downloaded...")
        val cacheDir = File(modelCacheDir)
        if (!cacheDir.exists()) return false
        for (file in cacheDir.listFiles())
            if (file.isDirectory && file.name == name) {
                ODLogger.logInfo("Model already downloaded")
                return true
            }
        return false
    }

    override fun downloadModel(model: ODModel): File? {
        ODLogger.logInfo("Downloading model from " + model.remoteUrl)
        if (!File(modelCacheDir).exists()) File(modelCacheDir).mkdirs()
        val modelUrl = URL(model.remoteUrl)
        val rbc = Channels.newChannel(modelUrl.openStream())
        ODLogger.logInfo("Downloading from ${model.remoteUrl}")
        val tmpFile = File.createTempFile(modelCacheDir + model.modelName, ".tar.gz")
        ODLogger.logInfo("Downloading to... ${tmpFile.absolutePath}")
        val fos = FileOutputStream(tmpFile)
        fos.channel.transferFrom(rbc, 0, java.lang.Long.MAX_VALUE)
        ODLogger.logInfo("Downloaded....")
        model.downloaded = true
        return tmpFile
        //model.graphLocation = modelCacheDir + model.modelName + "/"
    }

    override fun extractModel(modelFile: File): String {
        ODLogger.logInfo("Extracting model...")
        var basePath = modelCacheDir
        val tis = TarInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(modelFile))))

        var entry: TarEntry? = tis.nextEntry
        if (entry != null && entry.isDirectory) {
            File(modelCacheDir + entry.name).mkdirs()
            entry = tis.nextEntry
        }
        while (entry != null) {
            ODLogger.logInfo("Extract ${entry.name}")
            if (entry.name.contains("PaxHeader")) {
                entry = tis.nextEntry
                continue
            }
            if (entry.isDirectory) {
                File(modelCacheDir + entry.name).mkdirs()
                entry = tis.nextEntry
                continue
            }
            var count: Int
            val data = ByteArray(2048)

            if (entry.name.contains("frozen_inference_graph.pb")) {
                basePath = File(modelCacheDir, entry.name.substring(0, entry.name.indexOf
                ("frozen_inference_graph.pb"))).absolutePath
            }
            File(modelCacheDir, entry.name).mkdirs()
            File(modelCacheDir, entry.name).delete()
            val fos = FileOutputStream(modelCacheDir + entry.name)

            val dest = BufferedOutputStream(fos)

            count = tis.read(data)
            while (count != -1) {
                dest.write(data, 0, count)
                count = tis.read(data)
            }

            dest.flush()
            dest.close()

            entry = tis.nextEntry
        }
        ODLogger.logInfo("Extracting model... Complete")
        return basePath
    }

    override fun loadModel(model: ODModel) {
        clean()
        val modelPath = File(modelCacheDir, model.modelName)
        if (!checkDownloadedModel(model.modelName)) {
            val tmpFile = downloadModel(model)
            ODLogger.logInfo("Extraction Model....")
            if (tmpFile != null) {
                val newModel = File(extractModel(tmpFile))
                ODLogger.logInfo("Extracted...")
                if (modelPath != newModel) {
                    newModel.renameTo(modelPath)
                    ODLogger.logInfo("Renaming model dir to: ${modelPath.absolutePath}")
                }
            }
            tmpFile?.delete()
        }
        ODLogger.logInfo("Loading model.. ${model.modelName}")
        loadModel(File(modelPath, "saved_model/").absolutePath)
        //Files.delete(File(modelCacheDir+model.modelName+".tar.gz").toPath())
    }


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
                /*ODModel(4,
                        "faster_rcnn_resnet101_coco",
                        "http://download.tensorflow.org/models/object_detection/faster_rcnn_resnet101_coco_2018_01_28.tar.gz",
                        checkDownloadedModel("faster_rcnn_resnet101_coco")
                ),*/
                ODModel(5,
                        "ssd_resnet_50_fpn_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_resnet50_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz",
                        checkDownloadedModel("ssd_resnet_50_fpn_coco")
                )//,
                /*ODModel(
                        6,
                        "faster_rcnn_inception_resnet_v2_atrous_coco",
                        "http://download.tensorflow.org/models/object_detection/faster_rcnn_inception_resnet_v2_atrous_coco_2018_01_28.tar.gz",
                        checkDownloadedModel("faster_rcnn_inception_resnet_v2_atrous_coco")
                ),
                ODModel(
                        7,
                        "faster_rcnn_nas",
                        "http://download.tensorflow.org/models/object_detection/faster_rcnn_nas_coco_2018_01_28.tar.gz",
                        checkDownloadedModel("faster_rcnn_nas")
                ),
                ODModel(
                        8,
                        "faster_rcnn_nas_lowproposals_coco",
                        "http://download.tensorflow.org/models/object_detection/faster_rcnn_nas_lowproposals_coco_2018_01_28.tar.gz",
                        checkDownloadedModel("faster_rcnn_nas_lowproposals_coco")
                )*/
        )
}
