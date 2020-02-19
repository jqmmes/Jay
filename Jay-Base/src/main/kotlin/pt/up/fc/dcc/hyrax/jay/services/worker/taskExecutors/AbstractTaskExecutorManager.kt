package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors

abstract class AbstractTaskExecutorManager {
    private var taskExecutor: TaskExecutor? = null

    abstract val taskExecutors: Set<TaskExecutor>

    open fun setExecutor(taskExecutorUUID: String): Boolean {
        try {
            val taskExecutor = taskExecutors.find { taskExecutor -> taskExecutor.id == taskExecutorUUID }
                    ?: return false
            this.taskExecutor?.destroy()
            this.taskExecutor = taskExecutor
            this.taskExecutor?.init()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    open fun getCurrentExecutor(): TaskExecutor? {
        return this.taskExecutor
    }
}