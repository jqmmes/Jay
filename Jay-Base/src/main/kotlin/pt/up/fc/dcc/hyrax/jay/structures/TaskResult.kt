package pt.up.fc.dcc.hyrax.jay.structures

import java.io.Serializable

abstract class TaskResult : Serializable {
  internal var taskId: String? = null

  fun taskId(): String? {
    return taskId
  }
}