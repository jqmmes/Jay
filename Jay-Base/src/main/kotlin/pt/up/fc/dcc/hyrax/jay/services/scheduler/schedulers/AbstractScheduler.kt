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

package pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers

import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.util.*
import java.util.concurrent.CountDownLatch

abstract class AbstractScheduler(name: String) {

    val id: String = UUID.randomUUID().toString()
    private val nameId: String = name
    private var waitInit = CountDownLatch(1)

    open fun getName(): String {
        return nameId
    }

    abstract fun scheduleTask(task: Task): JayProto.Worker?
    abstract fun getWorkerTypes(): JayProto.WorkerTypes
    open fun setSetting(key: String, value: Any?, statusCallback: ((JayProto.Status) -> Unit)? = null) {
        statusCallback?.invoke(JayUtils.genStatusError())
    }

    open fun init() {
        JayLogger.logInfo("INIT", actions = arrayOf("SCHEDULER_ID=$id", "SCHEDULER_NAME=$nameId"))
        waitInit.countDown()
    }

    open fun destroy() {
        waitInit = CountDownLatch(1)
    }

    fun getProto(): JayProto.Scheduler {
        return JayProto.Scheduler.newBuilder().setId(id).setName(getName()).build()
    }

    fun waitInit() {
        waitInit.await()
    }

    open fun setSettings(settingsMap: Map<String, Any?>): JayProto.Status {
        var status = JayUtils.genStatusSuccess()
        for (k in settingsMap.keys) {
            setSetting(k, settingsMap[k]) { setting_status ->
                if (setting_status.code == JayProto.StatusCode.Error) {
                    status = JayUtils.genStatusError()
                }
            }
        }
        return status
    }
}