package pt.up.fc.dcc.hyrax.odlib.tensorflow

import org.tensorflow.SavedModelBundle
import org.tensorflow.Tensor
import org.tensorflow.types.UInt8
import pt.up.fc.dcc.hyrax.odlib.ODUtils
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
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


    override fun loadModel(path: String, label: String, score: Float) {
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
}
