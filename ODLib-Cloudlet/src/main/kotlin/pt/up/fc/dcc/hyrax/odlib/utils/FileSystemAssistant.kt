package pt.up.fc.dcc.hyrax.odlib.utils

import pt.up.fc.dcc.hyrax.odlib.interfaces.FileSystemAssistant
import java.io.File
import com.google.common.io.Files


@Suppress("UnstableApiUsage")
class FileSystemAssistant : FileSystemAssistant {

    private var tmpDir : File = createTempDir("ODLib-Cloud", directory = File("/tmp/"))

    override fun getByteArrayFast(id: String): ByteArray {
        return File("${this.javaClass.protectionDomain.codeSource.location.toURI().path.removeSuffix("/ODCloud.jar")}/assets/$id").readBytes()
    }

    override fun getAbsolutePath(): String {
        return ""
    }

    override fun readTempFile(fileId: String?): ByteArray {
        if (fileId == null) return ByteArray(0)
        return File(tmpDir, fileId).readBytes()
    }

    override fun createTempFile(data: ByteArray?): String {
        val tmpFile = createTempFile(prefix = "job", directory = tmpDir)
        Files.write(data ?: ByteArray(0), tmpFile)
        return tmpFile.name
    }

    override fun getByteArrayFromId(id: String): ByteArray {
        return ByteArray(0)
    }
}