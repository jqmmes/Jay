package pt.up.fc.dcc.hyrax.odlib.services.worker.status.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.content.IntentFilter
import android.os.BatteryManager
import pt.up.fc.dcc.hyrax.odlib.protoc.ODProto
import pt.up.fc.dcc.hyrax.odlib.services.worker.BatteryMonitor
import kotlin.math.roundToInt

class AndroidBatteryMonitor(val context: Context) : BatteryMonitor() {
    private val levelMonitor = BatteryLevelUpdatesReceiver()
    private val chargingStateMonitor = BatteryChargeStateUpdatesReceiver()

    override fun setCallbacks(levelChangeCallback: (Int) -> Unit, statusChangeCallback: (ODProto.Worker.BatteryStatus) -> Unit) {
        levelMonitor.setCallback(levelChangeCallback)
        chargingStateMonitor.setCallback(statusChangeCallback)
    }


    class BatteryChargeStateUpdatesReceiver:  BroadcastReceiver() {

        private var context : Context? = null

        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            var batteryStatus: Intent? = null
            var status = 0
            if ((action == Intent.ACTION_POWER_CONNECTED) || (action == Intent.ACTION_POWER_DISCONNECTED)) {
                batteryStatus = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                    context?.registerReceiver(null, ifilter)
                }
                status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: 0
            }

            if (action == Intent.ACTION_POWER_CONNECTED) {
                val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
                val usbCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
                val acCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_AC

                if (status == BatteryManager.BATTERY_STATUS_FULL) statusChangeCallback?.invoke(ODProto.Worker.BatteryStatus.CHARGED)
                if ((status == BatteryManager.BATTERY_STATUS_CHARGING) && acCharge) statusChangeCallback?.invoke(ODProto.Worker.BatteryStatus.CHARGING)
                if ((status == BatteryManager.BATTERY_STATUS_CHARGING) && usbCharge) statusChangeCallback?.invoke(ODProto.Worker.BatteryStatus.USB)

            } else if ((action == Intent.ACTION_POWER_DISCONNECTED) && (status == BatteryManager.BATTERY_STATUS_DISCHARGING)) { statusChangeCallback?.invoke(ODProto.Worker.BatteryStatus.DISCHARGING) }
        }

        private var statusChangeCallback: ((ODProto.Worker.BatteryStatus) -> Unit)? = null

        fun setCallback(statusChangeCallback: (ODProto.Worker.BatteryStatus) -> Unit) {
            this.statusChangeCallback = statusChangeCallback
        }

        fun setContext(context: Context) {
            this.context = context
        }
    }

    class BatteryLevelUpdatesReceiver:  BroadcastReceiver() {

        private var levelChangeCallback: ((Int) -> Unit)? = null

        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 0
            levelChangeCallback?.invoke(((level.toFloat() / scale.toFloat())*100f).roundToInt())
        }

        fun setCallback(levelChangeCallback: (Int) -> Unit) {
            this.levelChangeCallback = levelChangeCallback
        }

    }

    override fun monitor() {
        context.registerReceiver( levelMonitor, IntentFilter( ACTION_BATTERY_CHANGED ) )
        chargingStateMonitor.setContext(context)
        val filterRefreshUpdate = IntentFilter()
        filterRefreshUpdate.addAction(ACTION_POWER_CONNECTED)
        filterRefreshUpdate.addAction(ACTION_POWER_DISCONNECTED)
        context.registerReceiver(chargingStateMonitor, filterRefreshUpdate)
    }

    override fun destroy() {
        context.unregisterReceiver(chargingStateMonitor)
    }

}