package pt.up.fc.dcc.hyrax.jay.structures

import pt.up.fc.dcc.hyrax.jay.proto.JayProto

data class TaskExecutor(val id: String, val name: String, val description: String?) {
    internal fun getProto(): JayProto.TaskExecutor {
        return JayProto.TaskExecutor.newBuilder()
            .setId(id)
            .setName(name)
            .setDescription(description)
            .build()
    }
}