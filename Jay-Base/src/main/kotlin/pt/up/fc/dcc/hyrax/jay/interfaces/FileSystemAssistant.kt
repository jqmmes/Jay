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

package pt.up.fc.dcc.hyrax.jay.interfaces


/**
 * todo: create specific functions to read and store tasks. These functions should be internal
 */
interface FileSystemAssistant {
    fun getByteArrayFromId(id: String): ByteArray
    fun getByteArrayFast(id: String): ByteArray
    fun getAbsolutePath(): String
    fun readTempFile(fileId: String?): ByteArray
    fun createTempFile(data: ByteArray?): String
    fun clearTempFile(fileId: String?)
    fun getFileSizeFromId(id: String): Long
    fun cacheByteArrayWithId(id: String, bytes: ByteArray): Boolean
}