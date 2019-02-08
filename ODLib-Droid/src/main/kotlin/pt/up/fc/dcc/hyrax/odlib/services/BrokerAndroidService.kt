package pt.up.fc.dcc.hyrax.odlib.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import pt.up.fc.dcc.hyrax.odlib.ODLib
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService

internal class BrokerAndroidService() : Service() {

    override fun onCreate() {
        super.onCreate()
        val notification = ODLib.makeNotification(this, "Broker Service", "Service Running")
        startForeground(notification.first, notification.second)
        BrokerService.start(true)
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