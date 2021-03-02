package pt.up.fc.dcc.hyrax.jay.structures

import pt.up.fc.dcc.hyrax.jay.proto.JayProto

data class Scheduler(val id: String, val name: String, val description: String?) {
    internal fun getProto() : JayProto.Scheduler {
        return JayProto.Scheduler.newBuilder()
            .setId(id)
            .setName(name)
            .setDescription(description)
            .build()
    }
}
