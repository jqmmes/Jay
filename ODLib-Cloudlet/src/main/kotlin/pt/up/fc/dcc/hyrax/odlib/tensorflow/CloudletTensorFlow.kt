package pt.up.fc.dcc.hyrax.odlib.tensorflow

import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import org.tensorflow.SavedModelBundle
import org.tensorflow.Tensor
import org.tensorflow.types.UInt8
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.structures.Detection
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.structures.Model
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
    override fun loadModel(model: Model, completeCallback: ((ODProto.Status) -> Unit)?) {
        clean()
        val modelPath = File(modelCacheDir, model.modelName)
        if (!checkDownloadedModel(model.modelName)) {
            val tmpFile = downloadModel(model)
            ODLogger.logInfo("EXTRACT_MODEL_INIT")
            if (tmpFile != null) {
                val newModel = File(extractModel(tmpFile))
                ODLogger.logInfo("EXTRACT_MODEL_COMPLETE")
                if (modelPath != newModel) {
                    newModel.renameTo(modelPath)
                    ODLogger.logInfo("RENAME_MODEL_DIR", actions = *arrayOf("NEW_NAME=${modelPath.absolutePath}"))
                }
            }
            tmpFile?.delete()
        }
        ODLogger.logInfo("LOADING_MODEL_INIT", actions = *arrayOf("MODEL_NAME=${model.modelName}"))
        loadModel(File(modelPath, "saved_model/").absolutePath, completeCallback = completeCallback)
        ODLogger.logInfo("LOADING_MODEL_COMPLETE", actions = *arrayOf("MODEL_NAME=${model.modelName}"))
        //Files.delete(File(modelCacheDir+model.modelName+".tar.gz").toPath())
    }

    override var minimumScore: Float = 0.3f

    private var loadedModel: SavedModelBundle? = null
    private var modelClosed = true
    private var modelCacheDir: String = "/tmp/ODLib/Models/"

    override fun modelLoaded(model: Model): Boolean {
        if (modelClosed || loadedModel == null) return false
        return true
    }

    override fun clean() {
        if (loadedModel != null) loadedModel!!.close()
        loadedModel = null
        modelClosed = true
    }


    private fun loadModel(path: String, score: Float = minimumScore, @Suppress("UNUSED_PARAMETER") completeCallback: ((ODProto.Status) -> Unit)?) {
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

    override fun detectObjects(imgData: ByteArray): List<Detection> {
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

    override fun detectObjects(imgPath: String): List<Detection> {
        if (loadedModel == null || modelClosed) {
            throw (Exception("\"Model not loaded.\""))
        }
        return processOutputs(loadedModel!!, makeImageTensor(imgPath))
    }

    private fun resizeImage(imgData: ByteArray, maxSize: Int = 300): Image {
        ODLogger.logInfo("INIT")
        ODLogger.logInfo("READ_IMAGE_DATA_INIT", actions = *arrayOf("IMAGE_SIZE=${imgData.size}"))
        val image = ImageIO.read(ByteArrayInputStream(imgData))
        ODLogger.logInfo("READ_IMAGE_DATA_COMPLETE", actions = *arrayOf("IMAGE_SIZE=${imgData.size}"))
        if (image.width == maxSize && image.height <= maxSize ||
                image.width <= maxSize && image.height == maxSize) return image
        val scale = maxSize.toFloat() / max(image.width, image.height)
        ODLogger.logInfo("SCALE_IMAGE", actions = *arrayOf("IMAGE_SCALE=$scale"))
        return image.getScaledInstance(floor(image.width * scale).toInt(), floor(image.height * scale).toInt(),
                SCALE_FAST)
    }

    private fun processOutputs(model: SavedModelBundle, tensor: Tensor<UInt8>): List<Detection> {
        ODLogger.logInfo("INIT")
        ODLogger.logInfo("RUN_MODEL_INIT")
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
        ODLogger.logInfo("RUN_MODEL_COMPLETE")

        val returnList: MutableList<Detection> = LinkedList()

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
                        returnList.add(Detection(scores[i], classes[i].toInt()))
                    }
                }
            }
        }
        ODLogger.logInfo("COMPLETE")
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
        ODLogger.logInfo("INIT")
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
        ODLogger.logInfo("COMPLETE")
        return tensor
    }

    @Throws(IOException::class)
    private fun makeImageTensor(filename: String): Tensor<UInt8> {
        ODLogger.logInfo("INIT")
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
        val tensor =  Tensor.create(UInt8::class.java, shape, ByteBuffer.wrap(data))
        ODLogger.logInfo("COMPLETE")
        return tensor
    }

    @Suppress("unused")
    private fun convertBufferedImageType(bufferedImage: BufferedImage): BufferedImage {
        ODLogger.logInfo("INIT")
        val converted = BufferedImage(300, 300, BufferedImage.TYPE_3BYTE_BGR)
        if (bufferedImage.type == BufferedImage.TYPE_4BYTE_ABGR) {
            ODLogger.logInfo("Converting image to correct type (YPE_3BYTE_BGR)")
            ODLogger.logInfo("CONVERT_IMAGE_INIT", actions = *arrayOf("IMAGE_FORMAT=TYPE_3BYTE_BGR"))
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
            ODLogger.logInfo("CONVERT_IMAGE_COMPLETE", actions = *arrayOf("IMAGE_FORMAT=TYPE_3BYTE_BGR"))
        }
        ODLogger.logInfo("COMPLETE")
        return converted
    }

    override fun checkDownloadedModel(name: String): Boolean {
        ODLogger.logInfo("INIT")
        val cacheDir = File(modelCacheDir)
        if (!cacheDir.exists()) return false
        for (file in cacheDir.listFiles())
            if (file.isDirectory && file.name == name) {
                ODLogger.logInfo("COMPLETE", actions = *arrayOf("MODEL_LOADED=TRUE"))
                return true
            }
        ODLogger.logInfo("COMPLETE", actions = *arrayOf("MODEL_LOADED=FALSE"))
        return false
    }

    override fun downloadModel(model: Model): File? {
        ODLogger.logInfo("INIT", actions = *arrayOf("MODEL_NAME=${model.modelName}", "MODEL_ID=${model.modelId}", "MODEL_URL=${model.remoteUrl}"))
        if (!File(modelCacheDir).exists()) File(modelCacheDir).mkdirs()
        val modelUrl = URL(model.remoteUrl)
        val rbc = Channels.newChannel(modelUrl.openStream())
        val tmpFile = File.createTempFile(modelCacheDir + model.modelName, ".tar.gz")
        ODLogger.logInfo("DOWNLOAD_INIT", actions = *arrayOf("MODEL_NAME=${model.modelName}", "MODEL_ID=${model.modelId}", "MODEL_URL=${model.remoteUrl}", "DOWNLOAD_LOCATION=${tmpFile.absolutePath}"))
        val fos = FileOutputStream(tmpFile)
        fos.channel.transferFrom(rbc, 0, java.lang.Long.MAX_VALUE)
        model.downloaded = true
        ODLogger.logInfo("DOWNLOAD_COMPLETE", actions = *arrayOf("MODEL_NAME=${model.modelName}", "MODEL_ID=${model.modelId}", "MODEL_URL=${model.remoteUrl}", "DOWNLOAD_LOCATION=${tmpFile.absolutePath}"))
        return tmpFile
    }

    override fun extractModel(modelFile: File): String {
        ODLogger.logInfo("INIT")
        var basePath = modelCacheDir
        val tis = TarInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(modelFile))))

        var entry: TarEntry? = tis.nextEntry
        if (entry != null && entry.isDirectory) {
            File(modelCacheDir + entry.name).mkdirs()
            entry = tis.nextEntry
        }
        while (entry != null) {
            ODLogger.logInfo("EXTRACT", actions = *arrayOf("FILE_NAME=${entry.name}"))
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
        ODLogger.logInfo("COMPLETE")
        return basePath
    }

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
                /*Model(4,
                        "faster_rcnn_resnet101_coco",
                        "http://download.tensorflow.org/models/object_detection/faster_rcnn_resnet101_coco_2018_01_28.tar.gz",
                        checkDownloadedModel("faster_rcnn_resnet101_coco")
                ),*/
                Model(5,
                        "ssd_resnet_50_fpn_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_resnet50_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz",
                        checkDownloadedModel("ssd_resnet_50_fpn_coco")
                )//,
                /*Model(
                        6,
                        "faster_rcnn_inception_resnet_v2_atrous_coco",
                        "http://download.tensorflow.org/models/object_detection/faster_rcnn_inception_resnet_v2_atrous_coco_2018_01_28.tar.gz",
                        checkDownloadedModel("faster_rcnn_inception_resnet_v2_atrous_coco")
                ),
                Model(
                        7,
                        "faster_rcnn_nas",
                        "http://download.tensorflow.org/models/object_detection/faster_rcnn_nas_coco_2018_01_28.tar.gz",
                        checkDownloadedModel("faster_rcnn_nas")
                ),
                Model(
                        8,
                        "faster_rcnn_nas_lowproposals_coco",
                        "http://download.tensorflow.org/models/object_detection/faster_rcnn_nas_lowproposals_coco_2018_01_28.tar.gz",
                        checkDownloadedModel("faster_rcnn_nas_lowproposals_coco")
                )*/
        )
}
