package pt.up.fc.dcc.hyrax.jay.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import pt.up.fc.dcc.hyrax.jay.Jay
import pt.up.fc.dcc.hyrax.jay.R
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.services.worker.status.battery.AndroidBatteryMonitor

internal class SchedulerAndroidService : Service() {

    override fun onCreate() {
        super.onCreate()
        val notification = Jay.makeNotification(this, "DroidJay Scheduler", "Running", icon = R.drawable.ic_bird_scheduler_border)
        startForeground(notification.first, notification.second)
        SchedulerService.start(true, AndroidBatteryMonitor(this))
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