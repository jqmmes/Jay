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
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.TaskInfo
import pt.up.fc.dcc.hyrax.jay.structures.WorkerInfo
import pt.up.fc.dcc.hyrax.jay.structures.WorkerType

class SingleDeviceScheduler(private val workerType: WorkerType) : AbstractScheduler("SingleDeviceScheduler") {

    private var worker: WorkerInfo? = null
    override var description: String? = "Single device scheduler that offload every task to a single host (Local, Remote[random] or Cloud)"

    override fun init() {
        JayLogger.logInfo("WORKER_TYPE=${workerType.name}")
        if (workerType != WorkerType.LOCAL) {
            SchedulerService.listenForWorkers(true) {
                JayLogger.logInfo("COMPLETE")
                SchedulerService.enableHeartBeats(getWorkerTypes()) { super.init() }
            }
        } else {
            SchedulerService.enableHeartBeats(getWorkerTypes()) { super.init() }
        }
    }

    override fun getName(): String {
        return "${super.getName()} [${workerType.name}]"
    }

    override fun scheduleTask(taskInfo: TaskInfo): WorkerInfo? {
        JayLogger.logInfo("INIT", taskInfo.getId(), actions = arrayOf("WORKER_ID=${worker?.id}"))
        if (worker == null) {
            for (w in SchedulerService.getWorkers(workerType).values) {
                if (w?.type == workerType) {
                    worker = w
                    break
                }
            }
        }
        JayLogger.logInfo("COMPLETE", taskInfo.getId(), actions = arrayOf("WORKER_ID=${worker?.id}"))
        return worker
    }

    override fun destroy() {
        JayLogger.logInfo("INIT")
        worker = null
        SchedulerService.disableHeartBeats()
        if (workerType == WorkerType.REMOTE) {
            SchedulerService.listenForWorkers(false)
        }
        JayLogger.logInfo("COMPLETE")
        super.destroy()
    }

    override fun getWorkerTypes(): Set<WorkerType> {
        return setOf(WorkerType.CLOUD, WorkerType.LOCAL, WorkerType.REMOTE)
    }
}