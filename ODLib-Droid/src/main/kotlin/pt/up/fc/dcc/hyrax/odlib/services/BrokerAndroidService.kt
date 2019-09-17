package pt.up.fc.dcc.hyrax.odlib.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat.startActivityForResult
import pt.up.fc.dcc.hyrax.odlib.ODLib
import pt.up.fc.dcc.hyrax.odlib.services.broker.BrokerService
import java.io.File

internal class BrokerAndroidService() : Service() {

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate() {
        super.onCreate()
        val notification = ODLib.makeNotification(this, "Broker Service", "Service Running")
        startForeground(notification.first, notification.second)
        BrokerService.start(true)
        val root = File(getExternalFilesDir(null)!!.absolutePath)
        println(getExternalFilesDir(null)!!.absolutePath)
        println(root.canRead())
        for (x: String in root.list())
            println(x)

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