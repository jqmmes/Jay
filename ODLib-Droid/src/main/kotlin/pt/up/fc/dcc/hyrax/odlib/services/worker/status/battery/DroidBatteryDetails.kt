package pt.up.fc.dcc.hyrax.odlib.services.worker.status.battery

import android.content.Context
//import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto.Worker.BatteryStatus
//import pt.up.fc.dcc.hyrax.odlib.services.worker.status.StatusManager
//import pt.up.fc.dcc.hyrax.odlib.utils.SystemStats
import java.lang.Thread.sleep
import kotlin.concurrent.thread

// TODO: Monitor Android Device Battery Status
object DroidBatteryDetails {

    fun monitorBattery(@Suppress("UNUSED_PARAMETER") context: Context) {
        thread (isDaemon = true, name = "monitorBattery") {
            while(true) {
                //StatusManager.setBatteryPercentage(SystemStats.getBatteryPercentage(context))
                //StatusManager.setBatteryStatus(BatteryStatus.DISCHARGING)
                sleep(10000)
            }
        }
    }
}