package pt.up.fc.dcc.hyrax.odlib.tensorflow

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import pt.up.fc.dcc.hyrax.odlib.ODModel
import pt.up.fc.dcc.hyrax.odlib.ODUtils
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import java.io.*
import java.util.zip.GZIPInputStream


internal class DroidTensorFlow(private val context: Context) : DetectObjects {
    override var minimumScore: Float = 0f

    private lateinit var localDetector : Classifier
    private val TF_OD_API_INPUT_SIZE : Long = 300L
    private var minimumConfidence : Float = 0.0f
    override val models: List<ODModel>
        get() = listOf(
                ODModel(0,
                        "ssd_mobilenet_v1_fpn_coco",
                        "http://download.tensorflow.org/models/object_detection/ssd_mobilenet_v1_fpn_shared_box_predictor_640x640_coco14_sync_2018_07_03.tar.gz",
                        checkDownloadedModel( "ssd_mobilenet_v1_fpn_coco")
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
                )
        )

    override fun close() {
        localDetector.close()
    }

    override fun getByteArrayFromImage(imgPath: String): ByteArray {
        val stream = ByteArrayOutputStream()
        BitmapFactory.decodeFile(imgPath).compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    override fun detectObjects(imgData: ByteArray) : List<ODUtils.ODDetection> {
        //Verificar como funciona o decodeByteArray
        return detectObjects(BitmapFactory.decodeByteArray(imgData, 10, 10))
    }

    fun detectObjects(imgData: Bitmap) : List<ODUtils.ODDetection> {
        val results : List<Classifier.Recognition> = localDetector.recognizeImage(imgData)
        val mappedRecognitions : MutableList<ODUtils.ODDetection> = ArrayList()
        for (result : Classifier.Recognition in results) {
            if (result.confidence!! >= minimumConfidence) {
                mappedRecognitions.add(ODUtils.ODDetection(score = result.confidence, class_ = result.title!!.toInt(), box = ODUtils.Box()))
            }
        }
        return mappedRecognitions
    }

    private fun loadModel(path: String, label: String, score: Float) {
        try {
            localDetector = TensorFlowObjectDetectionAPIModel.create(
                    Resources.getSystem().assets, path, label, TF_OD_API_INPUT_SIZE)
            //cropSize = TF_OD_API_INPUT_SIZE
        } catch (e: IOException) {
            //LOGGER.e("Exception initializing classifier!", e)
            //val toast = Toast.makeText(
            //        getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT)
            //toast.show()
            //finish()
        }

    }

    override fun loadModel(model: ODModel) {
        context.cacheDir
    }

    override fun setMinAcceptScore(score: Float) {
        minimumConfidence = score
    }

    override fun detectObjects(imgPath: String) : List<ODUtils.ODDetection> {
        return detectObjects(BitmapFactory.decodeFile(imgPath))
    }

    override fun checkDownloadedModel(name: String): Boolean {
        val modelCache = File(context.cacheDir.toURI().path + "Models/")
        if (!modelCache.exists()) return false
        for (file in modelCache.listFiles())
            if (file.isDirectory && file.name == name) return true
        return false
    }

    override fun downloadModel(model: ODModel) {
        val request = DownloadManager.Request(Uri.parse(model.remoteUrl))
        //request.setDescription("Some descrition")
        request.setTitle(model.modelName)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        //request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "name-of-the-file.ext")
        request.setDestinationInExternalFilesDir(context, null, context.cacheDir.toURI().path+"Models/")

        // get download service and enqueue file
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)

        class onComplete : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                //val path = context.getFilesDir().getPath() +"/" + model.modelName
                //val f = File(path)
                /*val myIntent = Intent(Intent.ACTION_VIEW);

                myIntent.setData(Uri.fromFile(f));
                startActivity(myIntent);*/
            }
        }
        context.registerReceiver(onComplete(), IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    override fun extractModel(model: ODModel) {
        if (checkDownloadedModel(model.modelName)) return
        val tis = TarInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(context.cacheDir.path+model.modelName+".tar.gz"))))

        var entry : TarEntry? = tis.nextEntry
        if (entry!= null && entry.isDirectory) {
            File(context.cacheDir.path + entry.name).mkdirs()
            model.graphLocation = context.cacheDir.path + entry.name
            entry = tis.nextEntry
        }
        while (entry != null) {
            if (entry.isDirectory) {
                File(context.cacheDir.path + entry.name).mkdirs()
                entry = tis.nextEntry
                continue
            }
            var count: Int
            val data = ByteArray(2048)
            val fos = FileOutputStream(context.cacheDir.path + entry.name)

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
}