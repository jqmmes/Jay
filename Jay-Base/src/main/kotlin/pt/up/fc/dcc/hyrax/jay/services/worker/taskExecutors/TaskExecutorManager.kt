/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors

import pt.up.fc.dcc.hyrax.jay.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.structures.TaskInfo
import java.io.Serializable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Suppress("unused")
object TaskExecutorManager {
    private var taskExecutor: TaskExecutor? = null
    private val taskExecutors: HashSet<TaskExecutor> = hashSetOf()
    private val calibrationTasksInfo: HashSet<TaskInfo> = hashSetOf()
    private val calibrationTasks: HashSet<Task> = hashSetOf()
    private val lock: Any = Object()
    private val lock2: Any = Object()

    internal var executorThreadPool: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * By default we use a SingleThreadExecutor
     *
     * Using this function, it is possible to change the executor used
     */
    fun setExecutorService(executor: ExecutorService) {
        executorThreadPool = executor
    }

    fun registerTaskExecutor(taskExecutor: TaskExecutor) {
        taskExecutors.add(taskExecutor)
    }

    fun generateTask(serializable: Serializable, deadline: Long? = null) : Task {
        return Task(serializable, deadline)
    }

    fun generateTask(bytes: ByteArray, deadline: Long? = null) : Task {
        return Task(bytes, deadline)
    }

    fun setCalibrationTasks(vararg task: Task) {
        task.forEach { t ->
            if (BrokerService.fsAssistant != null) {
                BrokerService.fsAssistant?.cacheTask(t.getProto())
            } else {
                synchronized(lock){ calibrationTasks.add(t) }
            }
            synchronized(lock2) { calibrationTasksInfo.add(t.info) }
        }
    }

    internal fun getCalibrationTasks(): Set<TaskInfo> {
        synchronized(lock) {
            calibrationTasks.forEach { t ->
                BrokerService.fsAssistant?.cacheTask(t.getProto())
                calibrationTasks.remove(t)
            }
        }
        return listCalibrationTasks().toSet()
    }

    fun listCalibrationTasks(): Set<TaskInfo> {
        return calibrationTasksInfo.toSet()
    }

    fun removeCalibrationTasks(vararg task: Task) {
        synchronized(lock2) { task.forEach { t -> calibrationTasksInfo.remove(t.info) } }
    }

    internal fun getTaskExecutors(): Set<TaskExecutor> {
        return this.taskExecutors
    }

    internal fun setExecutor(taskExecutorUUID: String): Boolean {
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

    internal fun getCurrentExecutor(): TaskExecutor? {
        return this.taskExecutor
    }
}