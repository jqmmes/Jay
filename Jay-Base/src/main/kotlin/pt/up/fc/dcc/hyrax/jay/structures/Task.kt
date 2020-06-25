package pt.up.fc.dcc.hyrax.jay.structures

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import java.util.*

class Task {

    val id: String
    val data: ByteArray
    val dataSize: Int

    constructor(taskData: ByteArray) {
        id = UUID.randomUUID().toString()
        data = taskData
        dataSize = data.size
    }

    internal constructor(taskData: JayProto.Task?) {
        id = taskData!!.id
        data = taskData.data.toByteArray()
        dataSize = data.size
    }

    internal constructor(taskData: JayProto.TaskDetails?) {
        id = taskData!!.id
        data = ByteArray(0)
        dataSize = taskData.dataSize
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

    internal fun getProto(): JayProto.Task? {
        return JayProto.Task.newBuilder().setId(id).setData(ByteString.copyFrom(data)).build()
    }
}