/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.droid_jay_app.utils

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import pt.up.fc.dcc.hyrax.droid_jay_app.interfaces.Classifier
import pt.up.fc.dcc.hyrax.droid_jay_app.tensorfow_task.tensorflow.COCODataLabels
import pt.up.fc.dcc.hyrax.droid_jay_app.tensorfow_task.tensorflow.Detection
import pt.up.fc.dcc.hyrax.droid_jay_app.tensorfow_task.tensorflow.Model
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.util.zip.GZIPInputStream

object TFUtils {

    fun checkDownloadedModel(context: Context, name: String, lite: Boolean): Boolean {
        val modelCache = File(context.cacheDir, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}")
        if (!modelCache.exists()) return false
        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        for (file in modelCache.listFiles())
            if (file.isDirectory && file.name == name) return true
        return false
    }

    private fun getModelPath(context: Context, model: Model, modelName: String, lite: Boolean): String {
        JayLogger.logInfo("INIT", actions = arrayOf("MODEL_ID=${model.modelId}"))
        var modelPath = File(context.cacheDir, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${model.modelName}").absolutePath
        if (!checkDownloadedModel(context, model.modelName, lite)) {
            JayLogger.logInfo("NEED_TO_DOWNLOAD_MODEL_INIT", actions = arrayOf("MODEL_ID=${model.modelId}"))
            val tmpFile = downloadModel(context, model, lite)
            JayLogger.logInfo("NEED_TO_DOWNLOAD_MODEL_COMPLETE", actions = arrayOf("MODEL_ID=${model.modelId}"))
            if (tmpFile != null) {
                try {
                    modelPath = extractModel(context, tmpFile, modelName, lite)
                    if (File(modelPath) != File(context.cacheDir, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${model.modelName}")) {
                        File(modelPath).renameTo(File(context.cacheDir, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${model.modelName}"))
                        JayLogger.logInfo("RENAME", actions = arrayOf("MODEL_ID=${model.modelId}", "NEW_NAME=${File(context.cacheDir, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${model.modelName}").absolutePath}"))
                        modelPath = File(context.cacheDir, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${model.modelName}").absolutePath
                    }
                } catch (e: EOFException) {
                    JayLogger.logError("ERROR", actions = arrayOf("MODEL_ID=${model.modelId}", "ERROR_MSG=BAD_EOF"))
                }
                tmpFile.delete()
            } else {
                JayLogger.logError("ERROR", actions = arrayOf("MODEL_ID=${model.modelId}", "ERROR_MSG=DOWNLOAD_FAILED"))
            }
        }
        JayLogger.logInfo("COMPLETE", actions = arrayOf("MODEL_ID=${model.modelId}"))
        return File(modelPath, modelName).absolutePath
    }

    fun loadModel(classifier: Classifier, context: Context, model: Model, modelName: String, tfOdApiInputSize: Int, assetManager: AssetManager? = null, isQuantized: Boolean? = null, numThreads: Int? = null, device: String? = null, lite: Boolean = false): Classifier {
        val modelPath = getModelPath(context, model, modelName, lite)
        JayLogger.logInfo("LOADING_MODEL", actions = arrayOf("MODEL_ID=${model.modelId}", "MODEL_PATH=$modelPath"))
        try {
            classifier.init(modelPath, tfOdApiInputSize, assetManager, isQuantized, numThreads, device)
            JayLogger.logInfo("LOADING_MODEL_COMPLETE", actions = arrayOf("MODEL_ID=${model.modelId}"))
        } catch (e: IOException) {
            JayLogger.logError("ERROR", actions = arrayOf("MODEL_PATH=$modelPath"))
        }
        return classifier
    }

    fun detectObjects(localDetector: Classifier?, minimumConfidence: Float, tfOdApiInputSize: Int, imgBitmap: Bitmap): List<Detection> {
        JayLogger.logInfo("INIT")
        if (localDetector == null) {
            JayLogger.logWarn("ERROR", actions = arrayOf("ERROR_MESSAGE=NO_MODEL_LOADED"))
            return emptyList()
        }
        JayLogger.logInfo("RECOGNIZE_IMAGE_INIT")
        val results: List<Classifier.Recognition> = localDetector.recognizeImage(
            ImageUtils.scaleImage(imgBitmap, tfOdApiInputSize)
        )
        JayLogger.logInfo("RECOGNIZE_IMAGE_COMPLETE")
        val mappedRecognitions: MutableList<Detection> = ArrayList()
        for (result: Classifier.Recognition? in results) {
            if (result == null) continue
            if (result.confidence == null) continue
            if (result.confidence >= minimumConfidence) {
                try {
                    mappedRecognitions.add(Detection(score = result.confidence, class_ = result.title!!.toFloat().toInt()))
                } catch (e: Exception) {
                    mappedRecognitions.add(Detection(score = result.confidence, class_ = COCODataLabels.classId(result.title!!)))
                }
            }
        }
        JayLogger.logInfo("COMPLETE")
        return mappedRecognitions
    }

    fun downloadModel(context: Context, model: Model, lite: Boolean): File? {
        JayLogger.logInfo("INIT", actions = arrayOf("MODEL_ID=${model.modelId}", "MODEL_NAME=${model.modelName}", "MODEL_URL=${model.remoteUrl}"))
        var count: Int
        if (File(context.cacheDir, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}").exists() || !File(context.cacheDir, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}").isDirectory) File(context.cacheDir, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}").mkdirs()
        val tmpFile = File.createTempFile(model.modelName + "-", ".tar.gz", File(context.cacheDir, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}"))
        try {
            val url = URL(model.remoteUrl)

            val connection: URLConnection = url.openConnection()
            connection.connect()

            // this will be useful so that you can show a typical 0-100% progress bar
            val lengthOfFile = connection.contentLength

            // download the file
            val input = BufferedInputStream(url.openStream(), 8192)
            JayLogger.logInfo("MODEL_INFO", actions = arrayOf("MODEL_ID=${model.modelId}", "MODEL_SIZE=$lengthOfFile"))

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
            JayLogger.logError("ERROR", actions = arrayOf("MODEL_ID=${model.modelId}"))
        }
        JayLogger.logInfo("COMPLETE", actions = arrayOf("MODEL_ID=${model.modelId}"))

        return tmpFile
    }

    private fun makeDir(context: Context, lite: Boolean, modelFile: File, name: String) {
        JayLogger.logInfo("MAKE_DIR", actions = arrayOf("MODEL_FILE=${modelFile.absolutePath}", "MK_DIR=${File(context.cacheDir.path, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${name}").path}"))
        File(context.cacheDir.path, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${name}").mkdirs()
    }

    fun extractModel(context: Context, modelFile: File, modelName: String, lite: Boolean): String {
        JayLogger.logInfo("INIT", actions = arrayOf("MODEL_FILE=${modelFile.absolutePath}"))
        val tis: TarInputStream = try {
            TarInputStream(BufferedInputStream(GZIPInputStream(FileInputStream(modelFile))))
        } catch (e: java.lang.Exception) {
            TarInputStream(BufferedInputStream(FileInputStream(modelFile)))
        }

        var basePath = File(context.cacheDir.path, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/").absolutePath
        var entry: TarEntry? = tis.nextEntry
        if (entry != null && entry.isDirectory) {
            makeDir(context, lite, modelFile, entry.name)
            basePath = File(context.cacheDir.path, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${entry.name}").absolutePath
            entry = tis.nextEntry
        }
        while (entry != null) {
            if (entry.name.contains("PaxHeader")) {
                entry = tis.nextEntry
                continue
            }
            if (entry.isDirectory) {
                makeDir(context, lite, modelFile, entry.name)
                entry = tis.nextEntry
                continue
            }
            if (entry.name.contains(modelName)) {
                basePath = File(context.cacheDir, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${entry.name.substring(0, entry.name.indexOf(modelName))}").absolutePath
            }
            var count: Int
            val data = ByteArray(2048)
            JayLogger.logInfo("EXTRACT_FILE", actions = arrayOf("MODEL_FILE=${modelFile.absolutePath}", "FILE=${File(context.cacheDir.path, entry.name).path}"))
            try {
                File(context.cacheDir.path, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${entry.name}").mkdirs()
                File(context.cacheDir.path, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${entry.name}").delete()
                val fos = FileOutputStream(File(context.cacheDir.path, "Models/${if (lite) "TensorflowLite" else "Tensorflow"}/${entry.name}"))

                val dest = BufferedOutputStream(fos)

                count = tis.read(data)
                while (count != -1) {
                    dest.write(data, 0, count)
                    count = tis.read(data)
                }

                dest.flush()
                dest.close()
            } catch (ignore: Exception) {
                JayLogger.logWarn("ERROR", actions = arrayOf("MODEL_FILE=${modelFile.absolutePath}", "EXTRACT=${File(context.cacheDir.path, entry.name).path}"))
            }
            entry = tis.nextEntry
        }
        JayLogger.logInfo("COMPLETE", actions = arrayOf("MODEL_FILE=${modelFile.absolutePath}"))
        return basePath
    }
}