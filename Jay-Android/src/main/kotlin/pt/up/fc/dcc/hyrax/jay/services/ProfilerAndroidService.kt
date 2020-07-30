package pt.up.fc.dcc.hyrax.jay.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import pt.up.fc.dcc.hyrax.jay.Jay
import pt.up.fc.dcc.hyrax.jay.R
import pt.up.fc.dcc.hyrax.jay.services.profiler.ProfilerService
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.AndroidBatteryMonitor
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.cpu.AndroidCPUManager
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
        ProfilerService.start(true, AndroidBatteryMonitor(this),
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