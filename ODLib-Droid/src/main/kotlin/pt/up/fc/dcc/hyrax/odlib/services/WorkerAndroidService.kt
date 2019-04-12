package pt.up.fc.dcc.hyrax.odlib.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import pt.up.fc.dcc.hyrax.odlib.ODLib
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.services.worker.status.battery.AndroidBatteryMonitor
import pt.up.fc.dcc.hyrax.odlib.tensorflow.DroidTensorFlow

internal class WorkerAndroidService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        WorkerService.start(DroidTensorFlow(this), true, batteryMonitor = AndroidBatteryMonitor(this))
        WorkerService.monitorBattery()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        val notification = ODLib.makeNotification(this, "Worker Service", "Service Running")
        startForeground(notification.first, notification.second)
    }


    override fun onDestroy() {
        WorkerService.stop()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}