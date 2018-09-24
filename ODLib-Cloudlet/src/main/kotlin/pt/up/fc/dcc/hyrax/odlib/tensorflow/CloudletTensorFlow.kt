package pt.up.fc.dcc.hyrax.odlib.tensorflow

import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import org.tensorflow.SavedModelBundle
import org.tensorflow.Tensor
import org.tensorflow.types.UInt8
import pt.up.fc.dcc.hyrax.odlib.ODModel
import pt.up.fc.dcc.hyrax.odlib.ODUtils
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.*
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.file.Files
import java.util.*
import java.util.zip.GZIPInputStream
import javax.imageio.ImageIO

/**
 * Java inference for the Object Detection API at:
 * https://github.com/tensorflow/models/blob/master/research/object_detection/
 */
internal class CloudletTensorFlow : DetectObjects {
    override fun close() {
        if (!::loadedModel.isInitialized) loadedModel.close()
        modelClosed = true
    }

    override var minimumScore: Float = 0.5f

    private lateinit var modelPath: String
    private lateinit var loadedModel : SavedModelBundle
    private var modelClosed = true


    //override fun loadModel(path: String, label: String, score: Float) {
    private fun loadModel(path: String, label: String? = null, score: Float = minimumScore) {
        modelPath = path
        loadedModel = SavedModelBundle.load(modelPath, "serve")
        modelClosed = false
        if (score != minimumScore) setMinAcceptScore(score)
    }

    override fun setMinAcceptScore(score: Float) {
        if (score in 0.0f..1.0f) minimumScore = score
    }

    override fun detectObjects(imgData: ByteArray) : List<ODUtils.ODDetection> {
        if (!::loadedModel.isInitialized or modelClosed) {
            throw (Exception("\"Model not loaded.\""))
        }
        return processOutputs(loadedModel, makeImageTensor(imgData))
    }

    override fun detectObjects(imgPath: String) : List<ODUtils.ODDetection> {
        if (!::loadedModel.isInitialized or modelClosed) {
            throw (Exception("\"Model not loaded.\""))
        }
        return processOutputs(loadedModel, makeImageTensor(imgPath))
    }

    private fun processOutputs(model: SavedModelBundle, tensor: Tensor<UInt8>) : List<ODUtils.ODDetection>  {
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

        val returnList : MutableList<ODUtils.ODDetection> = LinkedList()

        outputs!![0].expect(Float::class.javaObjectType).use { scoresT ->
            outputs!![1].expect(Float::class.javaObjectType).use { classesT ->
                outputs!![2].expect(Float::class.javaObjectType).use { _ ->//boxesT ->
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
                        returnList.add(ODUtils.ODDetection(scores[i], classes[i].toInt(), ODUtils.Box()))
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

    override fun getByteArrayFromImage(imgPath: String) : ByteArray{
        val img = ImageIO.read(File(imgPath))
        val output = ByteArrayOutputStream()
        ImageIO.write(img, "jpg", output)
        return output.toByteArray()
    }

    @Throws(IOException::class)
    private fun makeImageTensor(filename: String): Tensor<UInt8> {
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


    private fun makeImageTensor(imageData: ByteArray): Tensor<UInt8> {
        val img = ImageIO.read(ByteArrayInputStream(imageData))
        if (img.type != BufferedImage.TYPE_3BYTE_BGR) {
            throw IOException(
                    String.format(
                            "Expected 3-byte BGR encoding in BufferedImage, found %d (file: %s). This code could be made more robust",
                            img.type))
        }
        val data = (img.data.dataBuffer as DataBufferByte).data
        // ImageIO.read seems to produce BGR-encoded images, but the model expects RGB.
        bgr2rgb(data)
        val batchSize: Long = 1
        val channels: Long = 3
        val shape = longArrayOf(batchSize, img.height.toLong(), img.width.toLong(), channels)
        return Tensor.create(UInt8::class.java, shape, ByteBuffer.wrap(data))
    }



        private var cacheDir : String = "/tmp/ODLib/Models/"

        override fun checkDownloadedModel(name: String): Boolean {
            val cacheDir = File(cacheDir)
            if (!cacheDir.exists()) return false
            for (file in cacheDir.listFiles())
                if (file.isDirectory && file.name == name)
                    return true
            return false
        }

        override fun downloadModel(model: ODModel) {
            println("Downloading model from " + model.remoteUrl)
            if (!File(cacheDir).exists()) File(cacheDir).mkdirs()
            val modelUrl = URL(model.remoteUrl)
            val rbc = Channels.newChannel(modelUrl.openStream())
            val fos = FileOutputStream(cacheDir+model.modelName+".tar.gz")
            fos.channel.transferFrom(rbc, 0, java.lang.Long.MAX_VALUE)
            model.downloaded = true
            model.graphLocation = cacheDir + model.modelName + "/"
        }

        override fun extractModel(model: ODModel) {
            if (checkDownloadedModel(model.modelName)) return
            val tis = TarInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(cacheDir+model.modelName+".tar.gz"))))

            var entry : TarEntry? = tis.nextEntry
            if (entry!= null && entry.isDirectory) {
                File(cacheDir + entry.name).mkdirs()
                model.graphLocation = cacheDir + entry.name
                entry = tis.nextEntry
            }
            while (entry != null) {
                if (entry.isDirectory) {
                    File(cacheDir + entry.name).mkdirs()
                    entry = tis.nextEntry
                    continue
                }
                var count: Int
                val data = ByteArray(2048)
                val fos = FileOutputStream(cacheDir + entry.name)

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
        }

    override fun loadModel(model: ODModel) {
        if (!checkDownloadedModel(model.modelName)) downloadModel(model)
        println("Extraction Model....")
        extractModel(model)
        println("Extracted... Loading model")
        loadModel(model.graphLocation + "saved_model/")
        Files.delete(File(cacheDir+model.modelName+".tar.gz").toPath())
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
                ODModel(4,
                        "faster_rcnn_resnet101_coco",
                        "http://download.tensorflow.org/models/object_detection/faster_rcnn_resnet101_coco_2018_01_28.tar.gz",
                        checkDownloadedModel("faster_rcnn_resnet101_coco")
                ),
                ODModel(5,
                        "ssd_resnet_50_fpn_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_resnet50_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz",
                        checkDownloadedModel("ssd_resnet_50_fpn_coco")
                ),
                ODModel(
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
                )
        )
}
