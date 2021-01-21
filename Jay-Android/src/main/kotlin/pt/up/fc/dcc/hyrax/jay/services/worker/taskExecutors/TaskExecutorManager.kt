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

import android.content.Context
import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.services.worker.taskExecutors.AbstractTaskExecutorManager as Manager

class TaskExecutorManager(context: Context, fsAssistant: FileSystemAssistant) : Manager() {
    override val taskExecutors: HashSet<TaskExecutor> = hashSetOf(
            TensorflowTaskExecutor(context, fsAssistant = fsAssistant),
            TensorflowTaskExecutor(context, name = "TensorflowLite", lite = true, fsAssistant = fsAssistant)
    )
}