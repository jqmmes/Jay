package pt.up.fc.dcc.hyrax.odlib.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.app.NotificationCompat

class WorkerService : Service() {

    private val ONGOING_NOTIFICATION_ID: Int = 1

    override fun onCreate() {
        super.onCreate()

        val pendingIntent: PendingIntent =
                Intent(this, WorkerService::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(this, 0, notificationIntent, 0)
                }

        val notifyID = 3
        val CHANNEL_ID = "my_channel_01"// The id of the channel.
        val name = "BrokerChannel"// The user-visible name of the channel.
        val importance = NotificationManager.IMPORTANCE_MIN
        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            mNotificationManager.createNotificationChannel(mChannel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setOngoing(true)
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setContentTitle("Worker Service")
                .setContentText("Hello World!")
                .build()

        // Issue the notification.
        mNotificationManager.notify(notifyID, notification)
        startForeground(ONGOING_NOTIFICATION_ID, notification)
        /*} else {
            val notification = NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setContentTitle("Broker")
                    .setContentText("Service Running")
                    .setContentIntent(pendingIntent)
                    .setTicker("Ticker")
                    .build()
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }*/

        //SchedulerService.startService(true)
    }


    override fun onDestroy() {
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}