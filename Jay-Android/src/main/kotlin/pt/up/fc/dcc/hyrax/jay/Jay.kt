package pt.up.fc.dcc.hyrax.jay

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Messenger
import android.support.v4.app.NotificationCompat
import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.services.BrokerAndroidService
import pt.up.fc.dcc.hyrax.jay.services.ClientAndroidService
import pt.up.fc.dcc.hyrax.jay.services.SchedulerAndroidService
import pt.up.fc.dcc.hyrax.jay.services.WorkerAndroidService
import pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc.SchedulerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.worker.grpc.WorkerGRPCClient


class Jay(private val context: Context) : AbstractJay() {

    private val clientConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            clientService = Messenger(service)
            clientBound = true
        }

        override fun onServiceDisconnected(className: ComponentName) {
            clientService = null
            clientBound = false
        }
    }

    init {
        Intent(context, ClientAndroidService::class.java).also { intent -> context.bindService(intent, clientConnection, Context.BIND_AUTO_CREATE) }
    }

    private var clientService : Messenger? = null
    private var clientBound : Boolean = false

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun startBroker(fsAssistant: FileSystemAssistant?) {
        JayLogger.logInfo("INIT")
        if (serviceRunningBroker()) return
        val brokerIntent = Intent(context, BrokerAndroidService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(brokerIntent)
        } else {
            context.startService(brokerIntent)
        }
        JayLogger.logInfo("COMPLETE")
    }

    override fun startScheduler(fsAssistant: FileSystemAssistant?) {
        startBroker()
        JayLogger.logInfo("INIT")
        if (serviceRunningScheduler()) return
        val schedulerIntent = Intent(context, SchedulerAndroidService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(schedulerIntent)
        } else {
            context.startService(schedulerIntent)
        }
        JayLogger.logInfo("COMPLETE")
    }

    override fun startWorker() {
        startBroker()
        JayLogger.logInfo("INIT")
        if (serviceRunningWorker()) return
        val workerIntent = Intent(context, WorkerAndroidService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(workerIntent)
        } else {
            context.startService(workerIntent)
        }
        JayLogger.logInfo("COMPLETE")
    }

    private fun serviceRunningBroker() : Boolean {
        return isMyServiceRunning(BrokerAndroidService::class.java)
    }

    private fun serviceRunningWorker() : Boolean {
        return isMyServiceRunning(WorkerAndroidService::class.java)
    }

    private fun serviceRunningScheduler() : Boolean {
        return isMyServiceRunning(SchedulerAndroidService::class.java)
    }

    override fun stopBroker() {
        if (!serviceRunningBroker()) return
        stopWorker()
        stopScheduler()
        broker.stopService { context.stopService(Intent(context, BrokerAndroidService::class.java)) }
    }

    override fun stopWorker() {
        if (!serviceRunningWorker()) return
        WorkerGRPCClient("127.0.0.1").stopService {
            context.stopService(Intent(context, WorkerAndroidService::class.java))
            if (!serviceRunningScheduler()) stopBroker()
        }
    }

    override fun stopScheduler() {
        if (!serviceRunningScheduler()) return
        SchedulerGRPCClient("127.0.0.1").stopService {
            context.stopService(Intent(context, SchedulerAndroidService::class.java))
            if (!serviceRunningWorker()) stopBroker()
        }
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
        private const val CHANNEL_ID = "my_channel_01"// The id of the channel.
        private const val name = "BrokerChannel"// The user-visible name of the channel.
        @Suppress("DEPRECATION")
        private val importance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager.IMPORTANCE_MIN
        } else {
            Notification.PRIORITY_LOW
        }
        private const val GROUP_KEY_JAY_SERVICES = "pt.up.fc.dcc.hyrax.jay.SERVICES"

        internal fun makeNotification(context: Context, title: CharSequence, text: CharSequence, icon: Int = R.drawable.ic_bird_border): Pair<Int, Notification> {
            val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
                mNotificationManager.createNotificationChannel(mChannel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(icon)
                    .setOngoing(true)
                    .setPriority(importance)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setGroup(GROUP_KEY_JAY_SERVICES)
                    .build()
            return Pair(notifyID++, notification)
        }
    }
}