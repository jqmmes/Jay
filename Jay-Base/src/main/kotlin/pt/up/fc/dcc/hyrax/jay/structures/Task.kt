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
import java.io.Serializable
import java.util.*

class Task : Serializable {

    val info: TaskInfo
    var data: ByteArray

    internal constructor(taskData: ByteArray, deadline: Long? = null) {
        info = TaskInfo(taskData.size.toLong(), (if (deadline != null) deadline * 1000 else null), System.currentTimeMillis())
        data = taskData
    }

    internal constructor(dataSize: Long, deadline: Long? = null) {
        info = TaskInfo(dataSize, (if (deadline != null) deadline * 1000 else null), System.currentTimeMillis())
        data = ByteArray(0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Task

        return info.getId() == other.info.getId()
    }

    override fun hashCode(): Int {
        return info.getId().hashCode()
    }

    internal fun getProto(taskData: ByteArray? = null): JayProto.Task? {
        val taskInfo = JayProto.TaskInfo.newBuilder()
        if (info.deadline != null) taskInfo.deadline = info.deadline
        taskInfo.id = info.getId()
        taskInfo.creationTimeStamp = info.creationTimeStamp

        return JayProto.Task
            .newBuilder()
            .setInfo(taskInfo.build())
            .setData(ByteString.copyFrom(taskData ?: data))
            .build()
    }
}