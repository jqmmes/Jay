/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.utils

import com.google.common.io.Files
import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import java.io.File


@Suppress("UnstableApiUsage")
class FileSystemAssistant : FileSystemAssistant {

    private var tmpDir: File = createTempDir("Jay-x86", directory = File("/tmp/"))

    override fun getByteArrayFast(id: String): ByteArray {
        return File("${this.javaClass.protectionDomain.codeSource.location.toURI().path.removeSuffix("/Jay-x86.jar")}/assets/$id").readBytes()
    }

    override fun getAbsolutePath(): String {
        return ""
    }

    override fun readTempFile(fileId: String?): ByteArray {
        if (fileId == null) return ByteArray(0)
        return File(tmpDir, fileId).readBytes()
    }

    override fun createTempFile(data: ByteArray?): String {
        val tmpFile = createTempFile(prefix = "task", directory = tmpDir)
        Files.write(data ?: ByteArray(0), tmpFile)
        return tmpFile.name
    }

    override fun clearTempFile(fileId: String?) {

    }

    override fun getFileSizeFromId(id: String): Long {
        return File(tmpDir, id).length()
    }

    override fun getByteArrayFromId(id: String): ByteArray {
        return ByteArray(0)
    }
}