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
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.structures.Task
import java.io.ByteArrayInputStream
import java.io.File
import java.io.ObjectInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

@ExperimentalPathApi
class FileSystemAssistant : FileSystemAssistant {

    private val tmpDir: Path = createTempDirectory("Jay-x86")
    private val tasksDir: Path = Path(tmpDir.toString(), "tasks").createDirectory()

    /*override fun getByteArrayFast(id: String): ByteArray {
        return File("${this.javaClass.protectionDomain.codeSource.location.toURI().path.removeSuffix("/Jay-x86.jar")}/assets/$id").readBytes()
    }

    override fun getAbsolutePath(): String {
        return ""
    }

    override fun readTempFile(fileId: String?): ByteArray {
        if (fileId == null) return ByteArray(0)
        return File(tmpDir.toFile(), fileId).readBytes()
    }*/

    override fun createTempFile(name: String): File {
        return Files.createFile(tmpDir.resolve(name)).toFile()

        /*val tmpFile: Path = createTempFile(prefix = "task", directory = tmpDir)
        return try {
            Files.write(tmpFile, name ?: ByteArray(0))
            tmpFile.name
        } catch (e: IOException) {
            ""
        }*/
    }

    override fun getTempFile(name: String): File? {
        return tmpDir.resolve(name).toFile()
    }

    override fun deleteTempFile(name: String) {
        Files.deleteIfExists(tmpDir.resolve(name))
    }

    /*override fun getFileSizeFromId(id: String): Long {
        return File(tmpDir.toFile(), id).length()
    }

    override fun cacheByteArrayWithId(id: String, bytes: ByteArray): Boolean {
        return try {
            Files.createTempFile(tasksDir, id, ".task").writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }*/

    override fun cacheTask(task: JayProto.Task?): Boolean {
        return try {
            assert(task != null)
            Files.createTempFile(tasksDir, task!!.info.id, ".task").writeBytes(task.toByteArray())
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun readTask(taskInfo: JayProto.TaskInfo?): Task? {
        return try {
            assert(taskInfo != null)
            val taskProto = JayProto.Task.parseFrom(Files.readAllBytes(tasksDir.resolve("${taskInfo!!.id}.task")))
            ObjectInputStream(ByteArrayInputStream(taskProto.data.toByteArray())).readObject() as Task
        } catch (e: Exception) {
            null
        }
    }

    override fun deleteTask(taskInfo: JayProto.TaskInfo?) {
        if(taskInfo == null) return
        Files.deleteIfExists(tasksDir.resolve("${taskInfo.id}.task"))
    }

    /*override fun getByteArrayFromId(id: String): ByteArray {
        return ByteArray(0)
    }*/
}