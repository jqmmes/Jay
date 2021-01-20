/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.interfaces

interface FileSystemAssistant {
    fun getByteArrayFromId(id: String): ByteArray
    fun getByteArrayFast(id: String): ByteArray
    fun getAbsolutePath(): String
    fun readTempFile(fileId: String?): ByteArray
    fun createTempFile(data: ByteArray?): String
    fun clearTempFile(fileId: String?)
    fun getFileSizeFromId(id: String): Long
}