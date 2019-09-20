package pt.up.fc.dcc.hyrax.odlib.utils

import android.media.Image
import pt.up.fc.dcc.hyrax.odlib.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.odlib.logger.ODLogger
import pt.up.fc.dcc.hyrax.odlib.services.BrokerAndroidService
import java.io.File

class FileSystemAssistant(private val brokerAndroidService: BrokerAndroidService) : FileSystemAssistant {

    override fun getByteArrayFromId(id: String): ByteArray {
        ODLogger.logInfo("START", actions = *arrayOf("FILE_ID=$id"))
        val root = File(brokerAndroidService.getExternalFilesDir(null)!!.absolutePath)
        val data = if (id in root.list())
            ImageUtils.getByteArrayFromImage(brokerAndroidService.getExternalFilesDir(null)!!.absolutePath+"/"+id)
        //ImageUtils.getByteArrayFromBitmapFast(ImageUtils.getImageBitmapFromFile(File(brokerAndroidService.getExternalFilesDir(null)!!.absolutePath+"/"+id)))
        else ByteArray(0)
        ODLogger.logInfo("COMPLETE", actions = *arrayOf("FILE_ID=$id", "DATA_SIZE=${data.size}"))
        return data
    }
}