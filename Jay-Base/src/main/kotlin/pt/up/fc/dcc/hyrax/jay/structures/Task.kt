package pt.up.fc.dcc.hyrax.jay.structures

import com.google.protobuf.ByteString
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import java.util.*

class Task {

    val id: String
    val data: ByteArray
    val dataSize: Int
    val creationTimeStamp: Long
    val deadline: Long?

    constructor(taskData: ByteArray, deadline: Long? = null) {
        id = UUID.randomUUID().toString()
        data = taskData
        dataSize = data.size
        creationTimeStamp = System.currentTimeMillis()
        if (deadline != null) this.deadline = creationTimeStamp + deadline else this.deadline = null
    }

    internal constructor(taskData: JayProto.Task?, deadline: Long? = null) {
        id = taskData!!.id
        data = taskData.data.toByteArray()
        dataSize = data.size
        creationTimeStamp = System.currentTimeMillis()
        if (deadline != null) this.deadline = creationTimeStamp + deadline else this.deadline = null
    }

    internal constructor(taskData: JayProto.TaskDetails?, deadline: Long? = null) {
        id = taskData!!.id
        data = ByteArray(0)
        dataSize = taskData.dataSize
        creationTimeStamp = System.currentTimeMillis()
        if (deadline != null) this.deadline = creationTimeStamp + deadline else this.deadline = null
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
        val proto = JayProto.Task
                .newBuilder()
                .setId(id)
                .setData(ByteString.copyFrom(data))
                .setCreationTimeStamp(creationTimeStamp)
        if (deadline != null) proto.deadlineTimeStamp = deadline
        return proto.build()
    }
}