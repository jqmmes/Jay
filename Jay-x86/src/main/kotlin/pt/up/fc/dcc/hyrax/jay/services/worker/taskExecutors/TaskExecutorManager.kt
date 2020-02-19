package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors

import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant

class TaskExecutorManager(fsAssistant: FileSystemAssistant) : AbstractTaskExecutorManager() {
    override val taskExecutors: Set<TaskExecutor> = setOf(TensorflowTaskExecutor(fsAssistant = fsAssistant))
}