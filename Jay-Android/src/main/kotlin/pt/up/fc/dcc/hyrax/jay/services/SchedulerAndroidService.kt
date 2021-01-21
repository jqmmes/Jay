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
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Worker
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power.AndroidPowerMonitor
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers.*

internal class SchedulerAndroidService : Service() {

    override fun onCreate() {
        super.onCreate()
        val notification = Jay.makeNotification(this, "DroidJay Scheduler", "Running", icon = R.drawable.ic_bird_scheduler_border)
        startForeground(notification.first, notification.second)
        SchedulerService.registerScheduler(SingleDeviceScheduler(Worker.Type.LOCAL))
        SchedulerService.registerScheduler(SingleDeviceScheduler(Worker.Type.CLOUD))
        SchedulerService.registerScheduler(SingleDeviceScheduler(Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.LOCAL))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.CLOUD, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.CLOUD, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.LOCAL))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.CLOUD, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.CLOUD, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(EstimatedTimeScheduler())
        SchedulerService.registerScheduler(ComputationEstimateScheduler())
        SchedulerService.registerScheduler(GreenTaskScheduler(Worker.Type.LOCAL))
        SchedulerService.registerScheduler(GreenTaskScheduler(Worker.Type.REMOTE))
        SchedulerService.registerScheduler(GreenTaskScheduler(Worker.Type.CLOUD))
        SchedulerService.registerScheduler(GreenTaskScheduler(Worker.Type.LOCAL, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(GreenTaskScheduler(Worker.Type.LOCAL, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(GreenTaskScheduler(Worker.Type.REMOTE, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(GreenTaskScheduler(Worker.Type.LOCAL, Worker.Type.REMOTE, Worker.Type.CLOUD))
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