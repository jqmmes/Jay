/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors

import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant

class TaskExecutorManager(fsAssistant: FileSystemAssistant) : AbstractTaskExecutorManager() {
    override val taskExecutors: HashSet<TaskExecutor> = hashSetOf(TensorflowTaskExecutor(fsAssistant = fsAssistant))
}