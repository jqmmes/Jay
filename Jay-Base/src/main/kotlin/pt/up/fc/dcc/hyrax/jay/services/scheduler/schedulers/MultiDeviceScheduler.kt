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
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.structures.Task
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import kotlin.random.Random

class MultiDeviceScheduler(private val roundRobin: Boolean = false, vararg devices: JayProto.Worker.Type) : AbstractScheduler("MultiDeviceScheduler") {

    private var devices = devices.toList()
    private var roundRobinCount: Int = 0

    override fun init() {
        JayLogger.logInfo("INIT", actions = arrayOf("SCHEDULE_STRATEGY=${if (roundRobin) "ROUND_ROBIN" else "RANDOM"}"))
        for (device in devices) JayLogger.logInfo("DEVICES", actions = arrayOf("DEVICE_TYPE=${device.name}"))
        if (JayProto.Worker.Type.REMOTE in devices) {
            SchedulerService.listenForWorkers(true) {
                SchedulerService.broker.enableHeartBeats(getWorkerTypes()) {
                    JayLogger.logInfo("COMPLETE")
                    super.init()
                }
            }
        } else {
            SchedulerService.broker.enableHeartBeats(getWorkerTypes()) {
                JayLogger.logInfo("COMPLETE")
                super.init()
            }
        }
    }

    override fun getName(): String {
        val strategy = if (roundRobin) "RoundRobin" else "Random"
        var devices = ""
        for (devType in this.devices) devices += "$devType, "
        return "${super.getName()} [$strategy] [${devices.trimEnd(' ', ',')}]"
    }

    override fun scheduleTask(task: Task): JayProto.Worker? {
        JayLogger.logInfo("INIT", task.id)
        val workers = SchedulerService.getWorkers(devices)
        val worker = when {
            workers.isEmpty() -> null
            roundRobin -> {
                roundRobinCount %= workers.size
                workers.values.toTypedArray()[roundRobinCount++]
            }
            else -> workers.values.toTypedArray()[if (workers.values.size > 1) Random.nextInt(workers.values.size) else 0]
        }
        JayLogger.logInfo("COMPLETE", actions = arrayOf("WORKER_ID=${worker?.id}"))
        return worker
    }

    override fun destroy() {
        JayLogger.logInfo("INIT")
        devices = emptyList()
        SchedulerService.broker.disableHeartBeats()
        roundRobinCount = 0
        if (JayProto.Worker.Type.REMOTE in devices) {
            SchedulerService.listenForWorkers(false)
        }
        JayLogger.logInfo("COMPLETE")
        super.destroy()
    }

    override fun getWorkerTypes(): JayProto.WorkerTypes {
        return JayUtils.genWorkerTypes(devices)
    }
}