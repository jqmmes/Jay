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