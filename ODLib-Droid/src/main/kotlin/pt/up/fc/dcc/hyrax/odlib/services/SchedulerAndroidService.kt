package pt.up.fc.dcc.hyrax.odlib.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import pt.up.fc.dcc.hyrax.odlib.ODLib
import pt.up.fc.dcc.hyrax.odlib.services.scheduler.SchedulerService

internal class SchedulerAndroidService : Service() {


    override fun onCreate() {
        super.onCreate()

        val notification = ODLib.makeNotification(this, "SchedulerBase Service", "Service Running")
        startForeground(notification.first, notification.second)
        SchedulerService.start(true)
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