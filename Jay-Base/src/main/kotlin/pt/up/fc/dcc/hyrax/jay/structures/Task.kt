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

package pt.up.fc.dcc.hyrax.jay.structures

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import java.util.*

class Task {

    val id: String
    var data: ByteArray
    val dataSize: Long
    val creationTimeStamp: Long
    val deadline: Long?
    val deadlineDuration: Long?
    val fileId: String?

    constructor(taskData: ByteArray, deadline: Long? = null) {
        id = UUID.randomUUID().toString()
        data = taskData
        dataSize = data.size.toLong()
        deadlineDuration = deadline
        creationTimeStamp = System.currentTimeMillis()
        fileId = null
        if (deadline != null) this.deadline = creationTimeStamp + (deadline * 1000) else this.deadline = null
    }

    internal constructor(fileId: String, dataSize: Long, deadline: Long? = null) {
        id = UUID.randomUUID().toString()
        data = ByteArray(0)
        this.fileId = fileId
        this.dataSize = dataSize
        deadlineDuration = deadline
        creationTimeStamp = System.currentTimeMillis()
        if (deadline != null) this.deadline = creationTimeStamp + (deadline * 1000) else this.deadline = null
    }

    internal constructor(oldTaskDetails: JayProto.TaskDetails?) {
        id = oldTaskDetails?.id ?: ""
        dataSize = oldTaskDetails?.dataSize ?: 0
        deadlineDuration = oldTaskDetails?.deadline
        data = ByteArray(0)
        fileId = null
        creationTimeStamp = oldTaskDetails?.creationTimeStamp ?: System.currentTimeMillis()
        deadline = if (oldTaskDetails != null) {
            creationTimeStamp + (oldTaskDetails.deadline * 1000)
        } else {
            null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Task

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }


    internal fun getProto(taskData: ByteArray? = null): JayProto.Task? {
        val proto = JayProto.Task
                .newBuilder()
                .setId(id)
                .setCreationTimeStamp(creationTimeStamp)
                .setData(ByteString.copyFrom(taskData ?: data))
        if (fileId != null) proto.fileId = fileId
        if (deadline != null) proto.deadlineTimeStamp = deadline
        if (deadlineDuration != null) proto.deadline = deadlineDuration
        return proto.build()
    }
}