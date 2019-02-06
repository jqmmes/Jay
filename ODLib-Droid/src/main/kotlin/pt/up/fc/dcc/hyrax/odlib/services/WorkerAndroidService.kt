package pt.up.fc.dcc.hyrax.odlib.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import pt.up.fc.dcc.hyrax.odlib.ODLib
import pt.up.fc.dcc.hyrax.odlib.R
import pt.up.fc.dcc.hyrax.odlib.interfaces.DetectObjects
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService

internal class WorkerAndroidService : Service() {


    companion object {
        private var localDetector : DetectObjects? = null
        internal fun setDetector(detector: DetectObjects) {
            localDetector = detector
        }
    }

    override fun onCreate() {
        super.onCreate()

        val notification = ODLib.makeNotification(this, "Worker Service", "Service Running")
        startForeground(notification.first, notification.second)
        if (localDetector != null) WorkerService.start(localDetector!!, true)
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