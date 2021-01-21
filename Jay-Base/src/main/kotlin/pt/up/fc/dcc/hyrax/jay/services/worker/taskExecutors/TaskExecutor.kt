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

import pt.up.fc.dcc.hyrax.jay.proto.JayProto.StatusCode
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.WorkerTask
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils.genStatusError
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils.genStatusSuccess
import java.util.UUID.randomUUID
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Status as JayStatus

abstract class TaskExecutor(val name: String, val description: String?) {

    val id: String = randomUUID().toString()

    open fun init(vararg params: Any?) {}
    open fun destroy() {}

    abstract fun executeTask(task: WorkerTask?, callback: ((Any) -> Unit)?)
    abstract fun setSetting(key: String, value: Any?, statusCallback: ((JayStatus) -> Unit)? = null)
    abstract fun callAction(action: String, statusCallback: ((JayStatus, Any?) -> Unit)? = null, vararg args: Any)
    abstract fun runAction(action: String, statusCallback: ((JayStatus) -> Unit)? = null, vararg args: Any)

    // baseline
    abstract fun getDefaultResponse(callback: ((Any) -> Unit)?)

    open fun setSettings(settingsMap: Map<String, Any?>): JayStatus {
        var status = genStatusSuccess()
        for (k in settingsMap.keys) {
            setSetting(k, settingsMap[k]) { setting_status ->
                if (setting_status.code == StatusCode.Error) {
                    status = genStatusError()
                }
            }
        }
        return status
    }

    open fun getQueueSize(): Int {
        return Integer.MAX_VALUE
    }
}