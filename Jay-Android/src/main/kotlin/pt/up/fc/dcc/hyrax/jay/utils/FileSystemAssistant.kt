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

import android.content.Context
import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.structures.Task
import java.io.ByteArrayInputStream
import java.io.File
import java.io.ObjectInputStream

class FileSystemAssistant(private val androidContext: Context) : FileSystemAssistant {

    override fun createTempFile(name: String): File? {
        if (!androidContext.cacheDir.resolve("cache").isDirectory) {
            androidContext.cacheDir.resolve("cache").mkdirs()
        }
        return try {
            androidContext.cacheDir.resolve("cache").resolve("$name.tmp").createNewFile()
            return androidContext.cacheDir.resolve("cache").resolve("$name.tmp")
        } catch (e: Exception) {
            null
        }
    }

    override fun getTempFile(name: String): File? {
        if (!androidContext.cacheDir.resolve("cache").isDirectory) {
            androidContext.cacheDir.resolve("cache").mkdirs()
        }
        return try {
            if (androidContext.cacheDir.resolve("cache").resolve("$name.tmp").isFile) {
                androidContext.cacheDir.resolve("cache").resolve("$name.tmp")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun deleteTempFile(name: String) {
        try {
            if (androidContext.cacheDir.resolve("cache").resolve("$name.tmp").isFile) {
                androidContext.cacheDir.resolve("cache").resolve("$name.tmp").delete()
            }
        } catch (e: Exception) { }
    }

    override fun cacheTask(task: JayProto.Task?): Boolean {
        if (task == null) return false
        if (!androidContext.cacheDir.resolve("tasks").isDirectory) {
            androidContext.cacheDir.resolve("tasks").mkdirs()
        }
        return try {
            androidContext.cacheDir.resolve("tasks").resolve("${task.info.id}.task").writeBytes(task.toByteArray())
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun readTask(taskInfo: JayProto.TaskInfo?): Task? {
        return try {
            if (taskInfo == null || androidContext.cacheDir.resolve("tasks").resolve("${taskInfo.id}.task").exists()) {
                while ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()) + Runtime.getRuntime()
                        .freeMemory
                            () < androidContext.cacheDir.resolve("tasks").resolve("${taskInfo!!.id}.task").length() * 2
                ) {
                    Thread.sleep(100)
                }
                val taskProto = JayProto.Task.parseFrom(
                    androidContext.cacheDir.resolve("tasks").resolve("${taskInfo.id}.task").readBytes()
                )
                ObjectInputStream(ByteArrayInputStream(taskProto.data.toByteArray())).readObject() as Task
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun deleteTask(taskInfo: JayProto.TaskInfo?) {
        if (taskInfo == null) return
        try {
            androidContext.cacheDir.resolve("tasks").resolve("${taskInfo.id}.task").delete()
        } catch (e: Exception) { }
    }
}