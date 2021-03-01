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

package pt.up.fc.dcc.hyrax.jay.utils

import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import android.content.Context
import com.google.common.io.Files
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import java.io.File
import java.lang.Exception

/**
 *
 *
 */
class FileSystemAssistant(private val androidContext: Context) : FileSystemAssistant {

    override fun getAbsolutePath(): String {
        return androidContext.getExternalFilesDir(null)!!.absolutePath
    }

    override fun createTempFile(data: ByteArray?): String {
        val tmpFile = File.createTempFile("task", "", androidContext.cacheDir)
        @Suppress("UnstableApiUsage")
        Files.write(data ?: ByteArray(0), tmpFile)
        return tmpFile.name
    }

    override fun clearTempFile(fileId: String?) {
        //
    }

    override fun getFileSizeFromId(id: String): Long {
        return File(androidContext.getExternalFilesDir(null)!!.absolutePath + "/" + id).length()
    }

    override fun cacheByteArrayWithId(id: String, bytes: ByteArray): Boolean {
        if (!androidContext.cacheDir.resolve("tasks").isDirectory) {
            androidContext.cacheDir.resolve("tasks").mkdirs()
        }
        return try {
            androidContext.cacheDir.resolve("tasks").resolve("$id.task").writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun readTempFile(fileId: String?): ByteArray {
        JayLogger.logInfo("INIT", actions = arrayOf("FILE_ID=$fileId"))
        if (fileId == null) return ByteArray(0)
        synchronized(tmpLOCK) {
            val tmpFile = File("${androidContext.cacheDir}/$fileId")
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

    // todo: remove imageutils.
    override fun getByteArrayFromId(id: String): ByteArray {
        /*JayLogger.logInfo("INIT", actions = arrayOf("FILE_ID=$id"))
        val root = File(androidContext.getExternalFilesDir(null)!!.absolutePath)
        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") val data = if (id in root.list())
            pt.up.fc.dcc.hyrax.droid_jay_app.utils.ImageUtils.getByteArrayFromImage(androidContext.getExternalFilesDir(null)!!.absolutePath + "/" + id)
        else ByteArray(0)
        JayLogger.logInfo("COMPLETE", actions = arrayOf("FILE_ID=$id", "DATA_SIZE=${data.size}"))
        return data*/
        return ByteArray(0)
    }

    override fun getByteArrayFast(id: String): ByteArray {
        JayLogger.logInfo("INIT", actions = arrayOf("FILE_ID=$id"))
        synchronized(readLOCK) {
            val file = File(androidContext.getExternalFilesDir(null)!!.absolutePath + "/" + id)
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