package pt.up.fc.dcc.hyrax.odlib.utils

import android.app.Service
import com.google.common.io.Files
import pt.up.fc.dcc.hyrax.odlib.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import java.io.File


class FileSystemAssistant(private val androidService: Service) : FileSystemAssistant {

    override fun getAbsolutePath(): String {
        return androidService.getExternalFilesDir(null)!!.absolutePath
    }

    override fun createTempFile(data: ByteArray?): String {
        val tmpFile = File.createTempFile("job", "", androidService.cacheDir)
        @Suppress("UnstableApiUsage")
        Files.write(data ?: ByteArray(0), tmpFile)
        return tmpFile.name
    }

    override fun readTempFile(fileId: String?): ByteArray {
        ODLogger.logInfo("INIT", actions = *arrayOf("FILE_ID=$fileId"))
        if (fileId == null) return ByteArray(0)
        synchronized(tmpLOCK) {
            val tmpFile = File("${androidService.cacheDir}/$fileId")
            while ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()) + Runtime.getRuntime().freeMemory
                    () < tmpFile.length() * 2) {
                Thread.sleep(100)
            }
            ODLogger.logInfo("READ_BYTES_INIT", actions = *arrayOf("FILE_ID=${tmpFile.name}"))
            val byteArray = tmpFile.readBytes()
            ODLogger.logInfo("COMPLETE", actions = *arrayOf("FILE_ID=$fileId"))
            return byteArray
        }
    }

    override fun getByteArrayFromId(id: String): ByteArray {
        ODLogger.logInfo("INIT", actions = *arrayOf("FILE_ID=$id"))
        val root = File(androidService.getExternalFilesDir(null)!!.absolutePath)
        val data = if (id in root.list())
            ImageUtils.getByteArrayFromImage(androidService.getExternalFilesDir(null)!!.absolutePath + "/" + id)
        else ByteArray(0)
        ODLogger.logInfo("COMPLETE", actions = *arrayOf("FILE_ID=$id", "DATA_SIZE=${data.size}"))
        return data
    }

    override fun getByteArrayFast(id: String): ByteArray {
        ODLogger.logInfo("INIT", actions = *arrayOf("FILE_ID=$id"))
        synchronized(readLOCK) {
            val file = File(androidService.getExternalFilesDir(null)!!.absolutePath + "/" + id)
            while ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()) + Runtime.getRuntime().freeMemory
                    () < 150000000) {
                Thread.sleep(100)
            }
            ODLogger.logInfo("READ_BYTES_INIT", actions = *arrayOf("FILE_ID=${file.name}"))
            val byteArray = file.readBytes()
            ODLogger.logInfo("COMPLETE", actions = *arrayOf("FILE_ID=$id"))
            return byteArray
        }
    }

    companion object {
        private val readLOCK = Any()
        private val tmpLOCK = Any()
    }
}