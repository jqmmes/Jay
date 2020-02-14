package pt.up.fc.dcc.hyrax.jay.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import pt.up.fc.dcc.hyrax.jay.Jay
import pt.up.fc.dcc.hyrax.jay.R
import pt.up.fc.dcc.hyrax.jay.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.jay.services.worker.status.battery.AndroidBatteryMonitor
import pt.up.fc.dcc.hyrax.jay.services.worker.workers.TensorflowWorker
import pt.up.fc.dcc.hyrax.jay.structures.Detection
import pt.up.fc.dcc.hyrax.jay.tensorflow.DroidTensorflowLite
import pt.up.fc.dcc.hyrax.jay.utils.FileSystemAssistant

internal class WorkerAndroidService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskExecutor = TensorflowWorker<List<Detection>>("TensorflowWorker")
        //taskExecutor.init(DroidTensorflow(this))
        taskExecutor.init(DroidTensorflowLite(this))
        WorkerService.start(taskExecutor, DroidTensorflowLite(this), true, batteryMonitor = AndroidBatteryMonitor(this),
                fsAssistant = FileSystemAssistant(this))
        WorkerService.monitorBattery()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        val notification = Jay.makeNotification(this, "DroidJay Worker", "Running", icon = R.drawable.ic_bird_worker_border)
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