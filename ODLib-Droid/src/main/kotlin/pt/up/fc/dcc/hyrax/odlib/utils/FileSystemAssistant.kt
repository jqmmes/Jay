package pt.up.fc.dcc.hyrax.odlib.utils

import pt.up.fc.dcc.hyrax.odlib.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.services.BrokerAndroidService
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream


class FileSystemAssistant(private val brokerAndroidService: BrokerAndroidService) : FileSystemAssistant {

    val LOCK = Any()

    override fun getAbsolutePath(): String {
        return brokerAndroidService.getExternalFilesDir(null)!!.absolutePath
    }

    override fun getByteArrayFromId(id: String): ByteArray {
        ODLogger.logInfo("INIT", actions = *arrayOf("FILE_ID=$id"))
        val root = File(brokerAndroidService.getExternalFilesDir(null)!!.absolutePath)
        val data = if (id in root.list())
            ImageUtils.getByteArrayFromImage(brokerAndroidService.getExternalFilesDir(null)!!.absolutePath+"/"+id)
        //ImageUtils.getByteArrayFromBitmapFast(ImageUtils.getImageBitmapFromFile(File(brokerAndroidService.getExternalFilesDir(null)!!.absolutePath+"/"+id)))
        else ByteArray(0)
        ODLogger.logInfo("COMPLETE", actions = *arrayOf("FILE_ID=$id", "DATA_SIZE=${data.size}"))
        return data
    }

    override fun getByteArrayFast(id: String): ByteArray {
        ODLogger.logInfo("INIT", actions = *arrayOf("FILE_ID=$id"))
        synchronized(LOCK) {
            val file = File(brokerAndroidService.getExternalFilesDir(null)!!.absolutePath + "/" + id)
            while ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()) + Runtime.getRuntime().freeMemory
                    () < 150000000) {
                Thread.sleep(100)
            }
            val byteArray = ByteArray(file.length().toInt())
            val buf = BufferedInputStream(FileInputStream(file))
            buf.read(byteArray, 0, file.length().toInt())
            buf.close()
            return byteArray
        }
        ODLogger.logInfo("COMPLETE", actions = *arrayOf("FILE_ID=$id"))
    }
}