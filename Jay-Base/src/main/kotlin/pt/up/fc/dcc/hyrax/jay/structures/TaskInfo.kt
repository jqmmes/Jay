package pt.up.fc.dcc.hyrax.jay.structures

import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import java.util.*

data class TaskInfo(val dataSize: Long, val deadline: Long?, val creationTimeStamp: Long = 10L) {
    private var id: String = UUID.randomUUID().toString()

    fun getId() : String {
        return id
    }

    internal constructor(oldTaskDetails: JayProto.TaskInfo?) : this(oldTaskDetails!!.dataSize, oldTaskDetails.deadline, oldTaskDetails.creationTimeStamp) {
        this.id = oldTaskDetails.id
    }

    internal fun getProto(): JayProto.TaskInfo {
        val builder = JayProto.TaskInfo.newBuilder()
            .setId(id)
            .setDataSize(dataSize)
            .setCreationTimeStamp(creationTimeStamp)
        if (deadline != null) builder.deadline = deadline
        return builder.build()
    }
}