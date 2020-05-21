package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.content.IntentFilter
import android.os.BatteryManager
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import kotlin.math.roundToInt

/**
 * BATTERY_PROPERTY_CURRENT_NOW           (Sync) Instantaneous battery current in microamperes
 * BATTERY_PROPERTY_ENERGY_COUNTER        (Sync) Battery remaining energy in nanowatt-hours
 * EXTRA_VOLTAGE                          (ASync) current battery temperature
 * EXTRA_TEMPERATURE                      (ASync) current battery voltage level
 */

class AndroidBatteryMonitor(private val context: Context) : BatteryMonitor {
    private val levelMonitor = BatteryLevelUpdatesReceiver()
    private val chargingStateMonitor = BatteryChargeStateUpdatesReceiver()

    private val mBatteryManager: BatteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    override fun setCallbacks(levelChangeCallback: (Int, Int, Float) -> Unit, statusChangeCallback: (JayProto.Worker.BatteryStatus) -> Unit) {
        levelMonitor.setCallback(levelChangeCallback)
        chargingStateMonitor.setCallback(statusChangeCallback)
    }


    class BatteryChargeStateUpdatesReceiver : BroadcastReceiver() {

        private var context: Context? = null

        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            var batteryStatus: Intent? = null
            var status = 0
            if ((action == ACTION_POWER_CONNECTED) || (action == ACTION_POWER_DISCONNECTED)) {
                batteryStatus = IntentFilter(ACTION_BATTERY_CHANGED).let { ifilter ->
                    context?.registerReceiver(null, ifilter)
                }
                status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: 0
            }

            if (action == ACTION_POWER_CONNECTED) {
                val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
                val usbCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
                val acCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_AC

                if (status == BatteryManager.BATTERY_STATUS_FULL) statusChangeCallback?.invoke(JayProto.Worker.BatteryStatus.CHARGED)
                if ((status == BatteryManager.BATTERY_STATUS_CHARGING) && acCharge) statusChangeCallback?.invoke(JayProto.Worker.BatteryStatus.CHARGING)
                if ((status == BatteryManager.BATTERY_STATUS_CHARGING) && usbCharge) statusChangeCallback?.invoke(JayProto.Worker.BatteryStatus.USB)

            } else if ((action == ACTION_POWER_DISCONNECTED) && (status == BatteryManager.BATTERY_STATUS_DISCHARGING)) {
                statusChangeCallback?.invoke(JayProto.Worker.BatteryStatus.DISCHARGING)
            }
        }

        private var statusChangeCallback: ((JayProto.Worker.BatteryStatus) -> Unit)? = null

        fun setCallback(statusChangeCallback: (JayProto.Worker.BatteryStatus) -> Unit) {
            this.statusChangeCallback = statusChangeCallback
        }

        fun setContext(context: Context) {
            this.context = context
        }
    }

    class BatteryLevelUpdatesReceiver:  BroadcastReceiver() {
        // levelChangeCallback(Percentage, Voltage, Temperature)
        private var levelChangeCallback: ((Int, Int, Float) -> Unit)? = null

        @SuppressLint("UnsafeProtectedBroadcastReceiver")
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 0
            val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: 0
            val temperature = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: 0) / 10f
            levelChangeCallback?.invoke(((level.toFloat() / scale.toFloat()) * 100f).roundToInt(), voltage, temperature)
        }

        fun setCallback(levelChangeCallback: (Int, Int, Float) -> Unit) {
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
        try {
            context.unregisterReceiver(chargingStateMonitor)
        } catch (ignore: Exception) {
        }
    }

    override fun getBatteryCurrentNow(): Int {
        return mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    }

    override fun getBatteryRemainingEnergy(): Long {
        return mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
    }

    override fun getBatteryCharge(): Int {
        return mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    }
}