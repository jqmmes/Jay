package pt.up.fc.dcc.hyrax.odlib.services.worker.interfaces

abstract class TaskExecutorManager {

    abstract val taskExecutors: Set<TaskExecutor>

    abstract fun listExecutors(): Set<TaskExecutor>
    abstract fun setExecutor(taskExecutor: TaskExecutor)

    abstract fun getCurrentExecutor()
}