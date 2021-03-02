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

package pt.up.fc.dcc.hyrax.jay.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import pt.up.fc.dcc.hyrax.jay.Jay
import pt.up.fc.dcc.hyrax.jay.R
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power.AndroidPowerMonitor
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers.*
import pt.up.fc.dcc.hyrax.jay.structures.WorkerType

internal class SchedulerAndroidService : Service() {

    // todo: Extract these scheduler registrations from here
    override fun onCreate() {
        super.onCreate()
        val notification = Jay.makeNotification(this, "DroidJay Scheduler", "Running", icon = R.drawable.ic_bird_scheduler_border)
        startForeground(notification.first, notification.second)
        SchedulerManager.registerScheduler(SingleDeviceScheduler(WorkerType.LOCAL))
        SchedulerManager.registerScheduler(SingleDeviceScheduler(WorkerType.CLOUD))
        SchedulerManager.registerScheduler(SingleDeviceScheduler(WorkerType.REMOTE))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(true, WorkerType.LOCAL))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(true, WorkerType.REMOTE))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(true, WorkerType.CLOUD))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(true, WorkerType.LOCAL, WorkerType.CLOUD))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(true, WorkerType.LOCAL, WorkerType.REMOTE))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(true, WorkerType.CLOUD, WorkerType.REMOTE))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(true, WorkerType.LOCAL, WorkerType.CLOUD, WorkerType.REMOTE))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(false, WorkerType.LOCAL))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(false, WorkerType.REMOTE))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(false, WorkerType.CLOUD))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(false, WorkerType.LOCAL, WorkerType.CLOUD))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(false, WorkerType.LOCAL, WorkerType.REMOTE))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(false, WorkerType.CLOUD, WorkerType.REMOTE))
        SchedulerManager.registerScheduler(MultiDeviceScheduler(false, WorkerType.LOCAL, WorkerType.CLOUD, WorkerType.REMOTE))
        SchedulerManager.registerScheduler(EstimatedTimeScheduler())
        SchedulerManager.registerScheduler(ComputationEstimateScheduler())
        SchedulerManager.registerScheduler(GreenTaskScheduler(WorkerType.LOCAL))
        SchedulerManager.registerScheduler(GreenTaskScheduler(WorkerType.REMOTE))
        SchedulerManager.registerScheduler(GreenTaskScheduler(WorkerType.CLOUD))
        SchedulerManager.registerScheduler(GreenTaskScheduler(WorkerType.LOCAL, WorkerType.REMOTE))
        SchedulerManager.registerScheduler(GreenTaskScheduler(WorkerType.LOCAL, WorkerType.CLOUD))
        SchedulerManager.registerScheduler(GreenTaskScheduler(WorkerType.REMOTE, WorkerType.CLOUD))
        SchedulerManager.registerScheduler(GreenTaskScheduler(WorkerType.LOCAL, WorkerType.REMOTE, WorkerType.CLOUD))
        SchedulerService.start(true, AndroidPowerMonitor(this))
    }

    override fun onDestroy() {
        SchedulerService.stop()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}