package pt.up.fc.dcc.hyrax.odlib.tensorflow

//import object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMap
//import object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMapItem

//import com.google.protobuf.TextFormat
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import org.tensorflow.SavedModelBundle
import org.tensorflow.Tensor
import org.tensorflow.framework.MetaGraphDef
import org.tensorflow.framework.SignatureDef
import org.tensorflow.framework.TensorInfo
import org.tensorflow.types.UInt8

/**
 * Java inference for the Object Detection API at:
 * https://github.com/tensorflow/models/blob/master/research/object_detection/
 */
//class JobObjects(val modelPath: String, val labelPath: String, val imgPath: String, private val minimumScore: Float = 0.5f) {
class DetectObjects {

    private lateinit var modelPath: String
    private lateinit var labelPath: String
    private lateinit var loadedModel : SavedModelBundle
    private lateinit var labels : Array<String?>
    private var minimumScore: Float = 0.5f


    internal fun setModel(path: String, label: String = String(), score: Float = minimumScore) {
        modelPath = path
        loadedModel = SavedModelBundle.load(modelPath, "serve")
        if (!label.isEmpty()) setLabels(label)
        if (score != minimumScore) setScore(score)
    }

    internal fun setLabels(label: String) {
        labelPath = label
        labels = loadLabels(labelPath)
    }

    internal fun setScore(score: Float) {
        if (score in 0.0f..1.0f) minimumScore = score
    }

    internal fun detectObjects(imgPath: String) {
        // TODO: Check model is loaded
        if (!::loadedModel.isInitialized) {
            println("Model not loaded.")
            return
        }
        loadedModel.use { model ->
            var outputs: List<Tensor<*>>? = null
            makeImageTensor(imgPath).use { input ->
                outputs = model
                        .session()
                        .runner()
                        .feed("image_tensor", input)
                        .fetch("detection_scores")
                        .fetch("detection_classes")
                        .fetch("detection_boxes")
                        .run()
            }
            outputs!![0].expect(Float::class.java).use { scoresT ->
                outputs!![1].expect(Float::class.java).use { classesT ->
                    outputs!![2].expect(Float::class.java).use { _ ->//boxesT ->
                        // All these tensors have:
                        // - 1 as the first dimension
                        // - maxObjects as the second dimension
                        // While boxesT will have 4 as the third dimension (2 sets of (x, y) coordinates).
                        // This can be verified by looking at scoresT.shape() etc.
                        val maxObjects = scoresT.shape()[1].toInt()
                        val scores = scoresT.copyTo(Array(1) { FloatArray(maxObjects) })[0]
                        val classes = classesT.copyTo(Array(1) { FloatArray(maxObjects) })[0]
                        //val boxes = boxesT.copyTo(Array(1) { Array(maxObjects) { FloatArray(4) } })[0]
                        // Print all objects whose score is at least 0.5.
                        //System.out.printf("* %s\n", filename)
                        System.out.printf("* %s\n", imgPath)
                        var foundSomething = false
                        for (i in scores.indices) {
                            if (scores[i] < minimumScore) {
                                continue
                            }
                            foundSomething = true
                            System.out.printf("\tFound %-20s (score: %.4f)\n", labels[classes[i].toInt()], scores[i])
                        }
                        if (!foundSomething) {
                            println("No objects detected with a high enough score.")
                        }
                    }
                }
            }

        }
    }

    /*@Throws(Exception::class)
    //@JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 3) {
            printUsage(System.err)
            System.exit(1)
        }
        //val labels = loadLabels(args[1])
//        val labels = loadLabels(labelPath)
        //SavedModelBundle.load(args[0], "serve").use { model ->
        //val loadedModel = SavedModelBundle.load(modelPath, "serve")
        loadedModel.use { model ->
        //SavedModelBundle.load(modelPath, "serve").use { model ->
            //printSignature(model)
            //for (arg in 2 until args.size) {
                //val filename = args[arg]
                var outputs: List<Tensor<*>>? = null
                //makeImageTensor(filename).use { input ->
            makeImageTensor(imgPath).use { input ->
                    outputs = model
                            .session()
                            .runner()
                            .feed("image_tensor", input)
                            .fetch("detection_scores")
                            .fetch("detection_classes")
                            .fetch("detection_boxes")
                            .run()
                }
                outputs!![0].expect(Float::class.java).use { scoresT ->
                    outputs!![1].expect(Float::class.java).use { classesT ->
                        outputs!![2].expect(Float::class.java).use { boxesT ->
                            // All these tensors have:
                            // - 1 as the first dimension
                            // - maxObjects as the second dimension
                            // While boxesT will have 4 as the third dimension (2 sets of (x, y) coordinates).
                            // This can be verified by looking at scoresT.shape() etc.
                            val maxObjects = scoresT.shape()[1].toInt()
                            val scores = scoresT.copyTo(Array(1) { FloatArray(maxObjects) })[0]
                            val classes = classesT.copyTo(Array(1) { FloatArray(maxObjects) })[0]
                            //val boxes = boxesT.copyTo(Array(1) { Array(maxObjects) { FloatArray(4) } })[0]
                            // Print all objects whose score is at least 0.5.
                            //System.out.printf("* %s\n", filename)
                            System.out.printf("* %s\n", imgPath)
                            var foundSomething = false
                            for (i in scores.indices) {
                                if (scores[i] < minimumScore) {
                                    continue
                                }
                                foundSomething = true
                                System.out.printf("\tFound %-20s (score: %.4f)\n", labels[classes[i].toInt()], scores[i])
                            }
                            if (!foundSomething) {
                                println("No objects detected with a high enough score.")
                            }
                        }
                    }
                //}
            }
        }
    }*/

    /*@Throws(Exception::class)
    private fun printSignature(model: SavedModelBundle) {
        val m = MetaGraphDef.parseFrom(model.metaGraphDef())
        val sig = m.getSignatureDefOrThrow("serving_default")
        //val numInputs = sig.getInputsCount()
        //var i = 1
        println("MODEL SIGNATURE")
        println("Inputs:")
        /*for (entry in sig.getInputsMap().entrySet()) {ByteArray(1)
            val t = entry.value
            System.out.printf(
                    "%d of %d: %-20s (Node name in graph: %-20s, type: %s)\n",
                    i++, numInputs, entry.key, t.getName(), t.getDtype())
        }*/
        val numOutputs = sig.getOutputsCount()
        i = 1
        println("Outputs:")
        /*for (entry in sig.getOutputsMap().entrySet()) {
            val t = entry.value
            System.out.printf(
                    "%d of %d: %-20s (Node name in graph: %-20s, type: %s)\n",
                    i++, numOutputs, entry.key, t.getName(), t.getDtype())
        }*/
        println("-----------------------------------------------")
    }*/

    @Throws(Exception::class) // Protobuf
    // TODO: Load Labels
    private fun loadLabels(filename: String): Array<String?> {
        val text = String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8)
        /*val builder = StringIntLabelMap.newBuilder()
        TextFormat.merge(text, builder)
        val proto = builder.build()*/
        //var maxId = 0
        val maxId = 0
        /*for (item in proto.getItemList()) {
            if (item.getId() > maxId) {
                maxId = item.getId()
            }
        }*/
        //val ret = arrayOfNulls<String>(maxId + 1)
        /*for (item in proto.getItemList()) {
            ret[item.getId()] = item.getDisplayName()
        }*/
        //return ret
        return arrayOfNulls(maxId + 1)
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

    /*private fun printUsage(s: PrintStream) {
        s.println("USAGE: <model> <label_map> <image> [<image>] [<image>]")
        s.println("")
        s.println("Where")
        s.println("<model> is the path to the SavedModel directory of the model to use.")
        s.println("        For example, the saved_model directory in tarballs from ")
        s.println(
                "        https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md)")
        s.println("")
        s.println(
                "<label_map> is the path to a file containing information about the labels detected by the model.")
        s.println("            For example, one of the .pbtxt files from ")
        s.println(
                "            https://github.com/tensorflow/models/tree/master/research/object_detection/data")
        s.println("")
        s.println("<image> is the path to an image file.")
        s.println("        Sample images can be found from the COCO, Kitti, or Open Images dataset.")
        s.println(
                "        See: https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md")
    }*/
}
