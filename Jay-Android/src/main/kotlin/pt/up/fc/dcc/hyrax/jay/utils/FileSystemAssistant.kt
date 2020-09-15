package pt.up.fc.dcc.hyrax.jay.utils

import android.app.Service
import com.google.common.io.Files
import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import java.io.File


class FileSystemAssistant(private val androidService: Service) : FileSystemAssistant {

    override fun getAbsolutePath(): String {
        return androidService.getExternalFilesDir(null)!!.absolutePath
    }

    override fun createTempFile(data: ByteArray?): String {
        val tmpFile = File.createTempFile("task", "", androidService.cacheDir)
        @Suppress("UnstableApiUsage")
        Files.write(data ?: ByteArray(0), tmpFile)
        return tmpFile.name
    }

    override fun clearTempFile(fileId: String?) {
        //
    }

    override fun getFileSizeFromId(id: String): Long {
        return File(androidService.getExternalFilesDir(null)!!.absolutePath + "/" + id).length()
    }

    override fun readTempFile(fileId: String?): ByteArray {
        JayLogger.logInfo("INIT", actions = arrayOf("FILE_ID=$fileId"))
        if (fileId == null) return ByteArray(0)
        synchronized(tmpLOCK) {
            val tmpFile = File("${androidService.cacheDir}/$fileId")
            while ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()) + Runtime.getRuntime().freeMemory
                    () < tmpFile.length() * 10) {
                Thread.sleep(100)
            }
            JayLogger.logInfo("AVAILABLE_MEMORY_READ_TMP_FILE", "", "AVAILABLE_MEMORY=${
                (Runtime.getRuntime().maxMemory
                () -
                        Runtime.getRuntime().totalMemory()) + Runtime.getRuntime().freeMemory
                ()
            }")
            JayLogger.logInfo("READ_BYTES_INIT", actions = arrayOf("FILE_ID=${tmpFile.name}"))
            val byteArray = tmpFile.readBytes()
            JayLogger.logInfo("COMPLETE", actions = arrayOf("FILE_ID=$fileId"))
            tmpFile.delete()
            return byteArray
        }
    }


    override fun getByteArrayFromId(id: String): ByteArray {
        JayLogger.logInfo("INIT", actions = arrayOf("FILE_ID=$id"))
        val root = File(androidService.getExternalFilesDir(null)!!.absolutePath)
        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") val data = if (id in root.list())
            ImageUtils.getByteArrayFromImage(androidService.getExternalFilesDir(null)!!.absolutePath + "/" + id)
        else ByteArray(0)
        JayLogger.logInfo("COMPLETE", actions = arrayOf("FILE_ID=$id", "DATA_SIZE=${data.size}"))
        return data
    }

    override fun getByteArrayFast(id: String): ByteArray {
        JayLogger.logInfo("INIT", actions = arrayOf("FILE_ID=$id"))
        synchronized(readLOCK) {
            val file = File(androidService.getExternalFilesDir(null)!!.absolutePath + "/" + id)
            while ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()) + Runtime.getRuntime().freeMemory
                    () < 150000000) {
                Thread.sleep(100)
            }
            JayLogger.logInfo("AVAILABLE_MEMORY_BYTEARRAY", "", "AVAILABLE_MEMORY=${
                (Runtime.getRuntime().maxMemory() -
                        Runtime.getRuntime().totalMemory()) + Runtime.getRuntime().freeMemory
                ()
            }")
            JayLogger.logInfo("READ_BYTES_INIT", actions = arrayOf("FILE_ID=${file.name}"))
            val byteArray = file.readBytes()
            JayLogger.logInfo("COMPLETE", actions = arrayOf("FILE_ID=$id"))
            return byteArray
        }
    }

    companion object {
        private val readLOCK = Any()
        private val tmpLOCK = Any()
    }
}