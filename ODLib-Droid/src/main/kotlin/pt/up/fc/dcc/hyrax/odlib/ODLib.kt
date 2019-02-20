package pt.up.fc.dcc.hyrax.odlib

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.support.v4.app.NotificationCompat
import android.content.Context
import android.content.Intent
import android.os.Build
import pt.up.fc.dcc.hyrax.odlib.services.worker.status.battery.DroidBatteryDetails
import pt.up.fc.dcc.hyrax.odlib.R
import pt.up.fc.dcc.hyrax.odlib.services.BrokerAndroidService
import pt.up.fc.dcc.hyrax.odlib.services.SchedulerAndroidService
import pt.up.fc.dcc.hyrax.odlib.services.WorkerAndroidService
import android.app.ActivityManager
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Messenger
import pt.up.fc.dcc.hyrax.odlib.services.ClientAndroidService


class ODLib(val context : Context) : AbstractODLib() {


    private val clientConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            clientService = Messenger(service)
            clientBound = true
        }

        override fun onServiceDisconnected(className: ComponentName) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            clientService = null
            clientBound = false
        }
    }


    init {
        DroidBatteryDetails.monitorBattery(context)
        Intent(context, ClientAndroidService::class.java).also { intent -> context.bindService(intent, clientConnection, Context.BIND_AUTO_CREATE)}
    }

    private var clientService : Messenger? = null
    private var clientBound : Boolean = false

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }


    override fun startBroker() {
        if (serviceRunningBroker()) return
        val brokerIntent = Intent(context, BrokerAndroidService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(brokerIntent)
        } else {
            context.startService(brokerIntent)
        }
    }

    override fun startScheduler() {
        startBroker()
        if (serviceRunningScheduler()) return
        val schedulerIntent = Intent(context, SchedulerAndroidService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(schedulerIntent)
        } else {
            context.startService(schedulerIntent)
        }
    }

    override fun startWorker() {
        startBroker()
        if (serviceRunningWorker()) return
        val workerIntent = Intent(context, WorkerAndroidService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(workerIntent)
        } else {
            context.startService(workerIntent)
        }
    }

    private fun serviceRunningBroker() : Boolean {
        return isMyServiceRunning(BrokerAndroidService::class.java)
    }

    fun serviceRunningWorker() : Boolean {
        return isMyServiceRunning(WorkerAndroidService::class.java)
    }

    fun serviceRunningScheduler() : Boolean {
        return isMyServiceRunning(SchedulerAndroidService::class.java)
    }

    override fun stopBroker() {
        if (!serviceRunningBroker()) return
        context.stopService(Intent(context, BrokerAndroidService::class.java))
    }

    override fun stopWorker() {
        if (!serviceRunningWorker()) return
        context.stopService(Intent(context, WorkerAndroidService::class.java))
        if (!serviceRunningScheduler()) stopBroker()

    }

    override fun stopScheduler() {
        if (!serviceRunningScheduler()) return
        context.stopService(Intent(context, SchedulerAndroidService::class.java))
        if (!serviceRunningWorker()) stopBroker()
    }


    override fun destroy(keepServices: Boolean) {
        super.destroy(keepServices)
        if (clientBound) {
            context.unbindService(clientConnection)
            clientBound = false
        }
    }



    internal companion object {
        private var notifyID = 1
        private val CHANNEL_ID = "my_channel_01"// The id of the channel.
        private val name = "BrokerChannel"// The user-visible name of the channel.
        private val importance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager.IMPORTANCE_MIN
        } else {
            Notification.PRIORITY_LOW
        }
        private val GROUP_KEY_ODLIB_SERVICES = "pt.up.fc.dcc.hyrax.odlib.SERVICES"

        internal fun makeNotification(context: Context, title: CharSequence, text: CharSequence) : Pair<Int, Notification> {
            val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
                mNotificationManager.createNotificationChannel(mChannel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setOngoing(true)
                    .setPriority(importance)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setGroup(GROUP_KEY_ODLIB_SERVICES)
                    .build()
            return Pair(notifyID++, notification)
        }
    }
}