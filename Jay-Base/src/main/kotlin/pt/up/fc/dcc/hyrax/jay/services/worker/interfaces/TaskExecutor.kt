package pt.up.fc.dcc.hyrax.jay.services.worker.interfaces

import java.util.*

data class TaskExecutor(val name: String, val description: String?) {
    val id: String = UUID.randomUUID().toString()
}