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
import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.cpu.AndroidCPUManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power.AndroidPowerMonitor
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.sensors.AndroidSensorManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport.AndroidTransportManager
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage.AndroidUsageManager
import java.io.File

class ProfilerAndroidService : Service() {

    override fun onCreate() {
        super.onCreate()
        val notification = Jay.makeNotification(this, "DroidJay Profiler", "Running", icon = R.drawable
                .ic_bird_scheduler_border)
        startForeground(notification.first, notification.second)
        AndroidTransportManager.setContext(this)
        AndroidUsageManager.setContext(this)
        AndroidSensorManager.setContext(this)
        ProfilerService.start(true, AndroidPowerMonitor(this),
                AndroidTransportManager, AndroidCPUManager, AndroidUsageManager,
                AndroidSensorManager, File(cacheDir, "profiler_recordings"))
    }

    override fun onDestroy() {
        ProfilerService.stop()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}