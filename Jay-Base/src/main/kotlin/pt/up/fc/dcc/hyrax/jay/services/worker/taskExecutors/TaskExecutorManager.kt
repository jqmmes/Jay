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

object TaskExecutorManager {
    private var taskExecutor: TaskExecutor? = null
    private val taskExecutors: HashSet<TaskExecutor> = hashSetOf()

    fun registerTaskExecutor(taskExecutor: TaskExecutor) {
        taskExecutors.add(taskExecutor)
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