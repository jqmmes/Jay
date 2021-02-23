/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 * 
 * Author: Joaquim Silva
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

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
import androidx.core.app.NotificationCompat
import pt.up.fc.dcc.hyrax.jay.interfaces.FileSystemAssistant
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.services.*
import pt.up.fc.dcc.hyrax.jay.services.scheduler.grpc.SchedulerGRPCClient
import pt.up.fc.dcc.hyrax.jay.services.worker.grpc.WorkerGRPCClient
import java.util.concurrent.atomic.AtomicBoolean


/**
 * todo: GetWorkerService, GetSchedulerService
 */
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

    private var clientService: Messenger? = null
    private var clientBound: Boolean = false
    private val brokerStarted: AtomicBoolean = AtomicBoolean(false)
    private val schedulerStarted: AtomicBoolean = AtomicBoolean(false)
    private val workerStarted: AtomicBoolean = AtomicBoolean(false)
    private val profilerStarted: AtomicBoolean = AtomicBoolean(false)

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

    private fun startService(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            JayLogger.logInfo("COMPLETE")
        } catch (ignore: Exception) {
            JayLogger.logError("START_FAIL")
        }
    }

    override fun startBroker(fsAssistant: FileSystemAssistant?) {
        JayLogger.logInfo("INIT")
        if (serviceRunningBroker() || !brokerStarted.compareAndSet(false, true)) return
        val brokerIntent = Intent(context, BrokerAndroidService::class.java)
        startService(brokerIntent)
        startProfiler()
    }

    override fun startScheduler(fsAssistant: FileSystemAssistant?) {
        startBroker()
        JayLogger.logInfo("INIT")
        if (serviceRunningScheduler() || !schedulerStarted.compareAndSet(false, true)) return
        val schedulerIntent = Intent(context, SchedulerAndroidService::class.java)
        startService(schedulerIntent)
    }

    override fun startWorker() {
        startBroker()
        JayLogger.logInfo("INIT")
        if (serviceRunningWorker() || !workerStarted.compareAndSet(false, true)) return
        val workerIntent = Intent(context, WorkerAndroidService::class.java)
        startService(workerIntent)
    }

    override fun startProfiler(fsAssistant: FileSystemAssistant?) {
        startBroker()
        JayLogger.logInfo("INIT")
        if (serviceRunningProfiler() || !profilerStarted.compareAndSet(false, true)) return
        val profilerIntent = Intent(context, ProfilerAndroidService::class.java)
        startService(profilerIntent)
    }

    private fun serviceRunningBroker(): Boolean {
        return isMyServiceRunning(BrokerAndroidService::class.java)
    }

    private fun serviceRunningWorker(): Boolean {
        return isMyServiceRunning(WorkerAndroidService::class.java)
    }

    private fun serviceRunningScheduler(): Boolean {
        return isMyServiceRunning(SchedulerAndroidService::class.java)
    }

    private fun serviceRunningProfiler(): Boolean {
        return isMyServiceRunning(ProfilerAndroidService::class.java)
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