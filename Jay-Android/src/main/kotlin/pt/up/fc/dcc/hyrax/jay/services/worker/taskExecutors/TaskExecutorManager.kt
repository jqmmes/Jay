package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors

import android.content.Context
import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.AbstractTaskExecutorManager as Manager

class TaskExecutorManager(context: Context, fsAssistant: FileSystemAssistant) : Manager() {
    override val taskExecutors: Set<TaskExecutor> = setOf(
            TensorflowTaskExecutor(context, fsAssistant = fsAssistant),
            TensorflowTaskExecutor(context, name = "TensorflowLite", lite = true, fsAssistant = fsAssistant)
    )
}