package pt.up.fc.dcc.hyrax.odlib.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.annotation.RequiresApi
import pt.up.fc.dcc.hyrax.odlib.ODLib
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.odlib.utils.FileSystemAssistant
import pt.up.fc.dcc.hyrax.odlib.utils.VideoUtils

class BrokerAndroidService() : Service() {

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate() {
        super.onCreate()
        val notification = ODLib.makeNotification(this, "Broker Service", "Service Running")
        startForeground(notification.first, notification.second)
        BrokerService.start(true, FileSystemAssistant(this), VideoUtils)
    }


    override fun onDestroy() {
        BrokerService.stop()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}