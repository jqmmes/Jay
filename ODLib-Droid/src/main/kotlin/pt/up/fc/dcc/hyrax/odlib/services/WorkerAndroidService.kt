package pt.up.fc.dcc.hyrax.odlib.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import pt.up.fc.dcc.hyrax.odlib.ODLib
import pt.up.fc.dcc.hyrax.odlib.services.worker.WorkerService
import pt.up.fc.dcc.hyrax.odlib.services.worker.status.battery.AndroidBatteryMonitor
import pt.up.fc.dcc.hyrax.odlib.services.worker.workers.TensorflowWorker
import pt.up.fc.dcc.hyrax.odlib.structures.Detection
import pt.up.fc.dcc.hyrax.odlib.tensorflow.DroidTensorflowLite
import pt.up.fc.dcc.hyrax.odlib.utils.FileSystemAssistant

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
        val notification = ODLib.makeNotification(this, "Worker Service", "Service Running")
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