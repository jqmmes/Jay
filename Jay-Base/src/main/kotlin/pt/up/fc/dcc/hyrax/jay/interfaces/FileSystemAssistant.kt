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

package pt.up.fc.dcc.hyrax.jay.interfaces

import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.structures.Task
import java.io.File

interface FileSystemAssistant {
    fun createTempFile(name: String): File?
    fun getTempFile(name: String): File?
    fun deleteTempFile(name: String)
    fun cacheTask(task: JayProto.Task?): Boolean
    fun readTask(taskInfo: JayProto.TaskInfo?): Task?
    fun deleteTask(taskInfo: JayProto.TaskInfo?)
}