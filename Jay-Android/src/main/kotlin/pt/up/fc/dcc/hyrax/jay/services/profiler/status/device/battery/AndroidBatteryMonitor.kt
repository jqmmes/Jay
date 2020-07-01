package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.content.IntentFilter
import android.os.BatteryManager
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import java.io.File
import kotlin.math.roundToInt

/**
 * BATTERY_PROPERTY_CURRENT_NOW           (Sync) Instantaneous battery current in microamperes
 * BATTERY_PROPERTY_ENERGY_COUNTER        (Sync) Battery remaining energy in nanowatt-hours
 * EXTRA_VOLTAGE                          (ASync) current battery temperature
 * EXTRA_TEMPERATURE                      (ASync) current battery voltage level
 *
 * todo: read system data directly to avoid unavailable information on some devices
 * todo: Save in a persistent manner the max read battery
 *
 *
 * s7e:
 * Must read battery and controler folders
 *
 *
 * Nexus9:
 * Reading battery details is forbidden, must use android api
 *
 * Lg g3:
 * Need to configure device and test it.
 *
 * tab s5e:
 * charging status:
 *  /sys/class/power_supply/usb/online
 *  /sys/class/power_supply/ac/online
 *  /sys/class/power_supply/wireless/online
 *  /sys/class/power_supply/otg/online
 *  /sys/class/power_supply/pogo/online
 *
 * temperature:
 *  /sys/class/power_supply/battery/temp
 *  /sys/class/power_supply/battery/batt_temp
 *
 * level:
 *  /sys/class/power_supply/battery/capacity
 *
 * current:
 *  /sys/class/power_supply/battery/current_now
 *  /sys/class/power_supply/battery/current_avg
 *
 * Capacity:
 *  /sys/class/power_supply/battery/charge_full
 *  /sys/class/power_supply/battery/charge_counter  (Valor *10, validar com a capacity e o charge_full)
 *
 *
 * All info in one file:
 *  /sys/class/power_supply/battery/uevent
 *
 *
 * Pixel 4:
 * All info in one file:
 *  /sys/class/power_supply/battery/uevent
 *
 * Important folders:
 *  /sys/class/power_supply/maxfg
 *  /sys/class/power_supply/battery/
 *
 *
 * https://elinux.org/images/4/45/Power-supply_Sebastian-Reichel.pdf
 * POWER_SUPPLY_CAPACITY - Power supply %
 *
 *
 * Charge Counter:
 * POWER_SUPPLY_CHARGE_NOW=22192904
 * POWER_SUPPLY_CHARGE_NOW=20772744
 * + POWER_SUPPLY_CHARGE_COUNTER_SHADOW
 *
 * POWER_SUPPLY_CHARGE_COUNTER=
 *
 *
 * POWER_SUPPLY_CHARGE_COUNTER
 *
 *
 * Power supply status (Charging, Full, Discharging, Not charging, Unknown):
 * POWER_SUPPLY_STATUS
 *
 *
 *
 * Total capacity (Some don't have):
 * POWER_SUPPLY_BATT_CE_FULL
 *
 * Capacity on s7e??
 * charge_otg_control:2821160
 * charge_uno_control:2821160
 *
 *
 * no Pixel4 a current lê ao contrário! positivo é consumo de energia, negativo é a carregar
 *
 *
 * No S7e e no N9 negativo é descarregar
 *
 *
 *
 * Ler o current com o getCurrentNow() e usar só isto nos que não dão info de charge_counter. nos outros usar também
 * o charge_counter. Senão, ler a percentagem de bateria e a current para estimar a carga.
 *
 *
 *
 *
 * A estatistica mais instantanea é a current no battery.
 * POWER_SUPPLY_CURRENT_NOW
 *
 * Current = Voltagem / Resistencia
 *
 * Resistencia: fg_fullcapnom
 * Voltagem: voltage_now
 *
 *
 *
 * static char *type_text[] = {
"Unknown", "Battery", "UPS", "Mains", "USB",
"USB_DCP", "USB_CDP", "USB_ACA",
"USB_HVDCP", "USB_HVDCP_3", "Wireless", "BMS", "USB_Parallel",
"Wipower", "TYPEC", "TYPEC_UFP", "TYPEC_DFP"
};
static char *status_text[] = {
"Unknown", "Charging", "Discharging", "Not charging", "Full"
};
static char *charge_type[] = {
"Unknown", "N/A", "Trickle", "Fast",
"Taper"
};
static char *health_text[] = {
"Unknown", "Good", "Overheat", "Dead", "Over voltage",
"Unspecified failure", "Cold", "Watchdog timer expire",
"Safety timer expire",
"Warm", "Cool"
};
static char *technology_text[] = {
"Unknown", "NiMH", "Li-ion", "Li-poly", "LiFe", "NiCd",
"LiMn"
};
static char *capacity_level_text[] = {
"Unknown", "Critical", "Low", "Normal", "High", "Full"
};
static char *scope_text[] = {
"Unknown", "System", "Device"
};
 *
 *
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

        private val baseDir: File = File("/sys/class/power_supply/")

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
                val qiCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS

                when (status) {
                    BatteryManager.BATTERY_STATUS_FULL -> statusChangeCallback?.invoke(JayProto.Worker.BatteryStatus.FULL)
                    BatteryManager.BATTERY_STATUS_CHARGING -> {
                        when {
                            acCharge -> statusChangeCallback?.invoke(JayProto.Worker.BatteryStatus.AC_CHARGING)
                            usbCharge -> statusChangeCallback?.invoke(JayProto.Worker.BatteryStatus.USB_CHARGING)
                            qiCharge -> statusChangeCallback?.invoke(JayProto.Worker.BatteryStatus.QI_CHARGING)
                        }
                    }
                }
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

    class BatteryLevelUpdatesReceiver : BroadcastReceiver() {
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
            JayLogger.logError("Failed to destroy AndroidBatteryMonitor")
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