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
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*


@ExperimentalPathApi
class FileSystemAssistant : FileSystemAssistant {

    //private var tmpDir: File = createTempDir("Jay-x86", directory = File("/tmp/"))
    private var tmpDir: Path = createTempDirectory("Jay-x86")

    override fun getByteArrayFast(id: String): ByteArray {
        return File("${this.javaClass.protectionDomain.codeSource.location.toURI().path.removeSuffix("/Jay-x86.jar")}/assets/$id").readBytes()
    }

    override fun getAbsolutePath(): String {
        return ""
    }

    override fun readTempFile(fileId: String?): ByteArray {
        if (fileId == null) return ByteArray(0)
        return File(tmpDir.toFile(), fileId).readBytes()
    }

    override fun createTempFile(data: ByteArray?): String {
        //val tmpFile = createTempFile(prefix = "task", directory = tmpDir)
        val tmpFile: Path = createTempFile(prefix = "task", directory = tmpDir)
        return try {
            Files.write(tmpFile, data ?: ByteArray(0))
            //Files.write(data ?: ByteArray(0), tmpFile.toFile())
            tmpFile.name
        } catch (e: IOException) {
            ""
        }
    }

    override fun clearTempFile(fileId: String?) {}

    override fun getFileSizeFromId(id: String): Long {
        return File(tmpDir.toFile(), id).length()
    }

    override fun getByteArrayFromId(id: String): ByteArray {
        return ByteArray(0)
    }
}