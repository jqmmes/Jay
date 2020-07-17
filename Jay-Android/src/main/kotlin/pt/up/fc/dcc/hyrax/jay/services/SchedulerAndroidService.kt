package pt.up.fc.dcc.hyrax.jay.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import pt.up.fc.dcc.hyrax.jay.Jay
import pt.up.fc.dcc.hyrax.jay.R
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.Worker
import pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery.AndroidBatteryMonitor
import pt.up.fc.dcc.hyrax.jay.services.scheduler.SchedulerService
import pt.up.fc.dcc.hyrax.jay.services.scheduler.schedulers.*

internal class SchedulerAndroidService : Service() {

    override fun onCreate() {
        super.onCreate()
        val notification = Jay.makeNotification(this, "DroidJay Scheduler", "Running", icon = R.drawable.ic_bird_scheduler_border)
        startForeground(notification.first, notification.second)
        SchedulerService.registerScheduler(SingleDeviceScheduler(Worker.Type.LOCAL))
        SchedulerService.registerScheduler(SingleDeviceScheduler(Worker.Type.CLOUD))
        SchedulerService.registerScheduler(SingleDeviceScheduler(Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.LOCAL))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.CLOUD, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(true, Worker.Type.LOCAL, Worker.Type.CLOUD, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.LOCAL))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.CLOUD, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(MultiDeviceScheduler(false, Worker.Type.LOCAL, Worker.Type.CLOUD, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(SmartScheduler())
        SchedulerService.registerScheduler(EstimatedTimeScheduler())
        SchedulerService.registerScheduler(ComputationEstimateScheduler())
        SchedulerService.registerScheduler(EAScheduler(Worker.Type.LOCAL))
        SchedulerService.registerScheduler(EAScheduler(Worker.Type.REMOTE))
        SchedulerService.registerScheduler(EAScheduler(Worker.Type.CLOUD))
        SchedulerService.registerScheduler(EAScheduler(Worker.Type.LOCAL, Worker.Type.REMOTE))
        SchedulerService.registerScheduler(EAScheduler(Worker.Type.LOCAL, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(EAScheduler(Worker.Type.REMOTE, Worker.Type.CLOUD))
        SchedulerService.registerScheduler(EAScheduler(Worker.Type.LOCAL, Worker.Type.REMOTE, Worker.Type.CLOUD))
        SchedulerService.start(true, AndroidBatteryMonitor(this))
    }

    override fun onDestroy() {
        SchedulerService.stop()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}