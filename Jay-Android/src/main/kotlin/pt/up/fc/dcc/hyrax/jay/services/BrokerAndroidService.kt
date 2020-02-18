package pt.up.fc.dcc.hyrax.jay.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.annotation.RequiresApi
import pt.up.fc.dcc.hyrax.jay.Jay
import pt.up.fc.dcc.hyrax.jay.R
import pt.up.fc.dcc.hyrax.jay.services.broker.BrokerService
import pt.up.fc.dcc.hyrax.jay.services.worker.status.battery.AndroidBatteryMonitor
import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.utils.VideoUtils

class BrokerAndroidService : Service() {

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate() {
        super.onCreate()
        val notification = Jay.makeNotification(this, "DroidJay Broker", "Running", icon = R.drawable.ic_bird_broker_border)
        startForeground(notification.first, notification.second)
        BrokerService.start(true, FileSystemAssistant(this), VideoUtils, AndroidBatteryMonitor(this))
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