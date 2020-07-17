package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.battery

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.BatteryManager.*
import android.os.Build
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.BatteryStatus
import pt.up.fc.dcc.hyrax.jay.utils.JayUtils
import java.io.*
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 *
 * N7: OK
 * S7e: OK
 * G2: Does not detect when is charging on USB and cannot compute remain charging time (need su)
 * S5e: OK -> NEEDS SCREEN ON
 * Pixel 4: OK
 *
 *
 * BATTERY_PROPERTY_CURRENT_NOW           (Sync) Instantaneous battery current in microamperes
 * BATTERY_PROPERTY_ENERGY_COUNTER        (Sync) Battery remaining energy in nanowatt-hours
 * EXTRA_VOLTAGE                          (ASync) current battery temperature
 * EXTRA_TEMPERATURE                      (ASync) current battery voltage level
 *
 * s7e & Nexus9:
 * Reading battery details is forbidden, must use android api
 *
 * Lg g2:
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
 * Ler o current com o getCurrentNow() e usar só isto nos que não dão info de charge_counter. nos outros usar também
 * o charge_counter. Senão, ler a percentagem de bateria e a current para estimar a carga.
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
 * "Unknown", "Battery", "UPS", "Mains", "USB",
 * "USB_DCP", "USB_CDP", "USB_ACA",
 * "USB_HVDCP", "USB_HVDCP_3", "Wireless", "BMS", "USB_Parallel",
 * "Wipower", "TYPEC", "TYPEC_UFP", "TYPEC_DFP"
 * };
 * static char *status_text[] = {
 * "Unknown", "Charging", "Discharging", "Not charging", "Full"
 * };
 * static char *charge_type[] = {
 * "Unknown", "N/A", "Trickle", "Fast",
 * "Taper"
 * };
 * static char *health_text[] = {
 * "Unknown", "Good", "Overheat", "Dead", "Over voltage",
 * "Unspecified failure", "Cold", "Watchdog timer expire",
 * "Safety timer expire",
 * "Warm", "Cool"
 * };
 * static char *technology_text[] = {
 * "Unknown", "NiMH", "Li-ion", "Li-poly", "LiFe", "NiCd",
 * "LiMn"
 * };
 * static char *capacity_level_text[] = {
 * "Unknown", "Critical", "Low", "Normal", "High", "Full"
 * };
 * static char *scope_text[] = {
 * "Unknown", "System", "Device"
 * };
 *
 *
 */

class AndroidBatteryMonitor(private val context: Context) : BatteryMonitor {
    private val levelMonitor = BatteryLevelUpdatesReceiver()
    private val chargingStateMonitor = BatteryChargeStateUpdatesReceiver()
    private val batteryDriverBaseDir = "/sys/class/power_supply"
    private val mBatteryManager: BatteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private var scale = 1
    private var signal = 1

    override fun setCallbacks(_levelChangeCallback: (Int, Int, Float) -> Unit,
                              _statusChangeCallback: (BatteryStatus) -> Unit) {
        statusChangeCallback = _statusChangeCallback
        levelChangeCallback = _levelChangeCallback
    }

    private companion object {
        var isChargingStatus: Boolean? = null
        var statusChangeCallback: ((BatteryStatus) -> Unit)? = null
        var levelChangeCallback: ((Int, Int, Float) -> Unit)? = null
    }

    class BatteryChargeStateUpdatesReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            var batteryStatus: Intent? = null
            var status = 0
            if ((action == ACTION_POWER_CONNECTED) || (action == ACTION_POWER_DISCONNECTED)) {
                batteryStatus = IntentFilter(ACTION_BATTERY_CHANGED).let { ifilter ->
                    context?.registerReceiver(null, ifilter)
                }
                status = batteryStatus?.getIntExtra(EXTRA_STATUS, -1) ?: 0
            }
            isChargingStatus = false
            if (action == ACTION_POWER_CONNECTED) {
                isChargingStatus = true
                when (status) {
                    BATTERY_STATUS_FULL -> statusChangeCallback?.invoke(BatteryStatus.FULL)
                    BATTERY_STATUS_CHARGING -> {
                        when (batteryStatus?.getIntExtra(EXTRA_PLUGGED, -1)) {
                            BATTERY_PLUGGED_AC -> statusChangeCallback?.invoke(BatteryStatus.AC_CHARGING)
                            BATTERY_PLUGGED_USB -> statusChangeCallback?.invoke(BatteryStatus.USB_CHARGING)
                            BATTERY_PLUGGED_WIRELESS -> statusChangeCallback?.invoke(BatteryStatus.QI_CHARGING)
                            else -> statusChangeCallback?.invoke(BatteryStatus.CHARGING)
                        }
                    }
                }
            } else if ((action == ACTION_POWER_DISCONNECTED) && (status == BATTERY_STATUS_DISCHARGING)) {
                statusChangeCallback?.invoke(BatteryStatus.DISCHARGING)
            } else if ((action == ACTION_POWER_DISCONNECTED)) {
                statusChangeCallback?.invoke(BatteryStatus.DISCHARGING)
            }
        }
    }

    class BatteryLevelUpdatesReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action != ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(EXTRA_SCALE, -1)
            val voltage = intent.getIntExtra(EXTRA_VOLTAGE, -1)
            val temperature = intent.getIntExtra(EXTRA_TEMPERATURE, -1) / 10f
            levelChangeCallback?.invoke(((level.toFloat() / scale.toFloat()) * 100f).roundToInt(), voltage, temperature)
        }
    }

    override fun monitor() {
        context.registerReceiver( levelMonitor, IntentFilter( ACTION_BATTERY_CHANGED ) )
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

    private fun isCharging(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (isChargingStatus == null)
                if (mBatteryManager.computeChargeTimeRemaining() >= 0) {
                    isChargingStatus = true
                }
        }

        if (JayUtils.isRooted()) {
            /**
             * Pixel 4:
             *   AC powered: false
             *   USB powered: true
             *   Wireless powered: false
             *   Max charging current: 500000
             *   Max charging voltage: 5000000
             *   Charge counter: 2282000
             *   status: 2
             *   health: 2
             *   present: true
             *   level: 75
             *   scale: 100
             *   voltage: 4120
             *   temperature: 313
             *   technology: Li-ion
             *
             *   Tab S5e:
             *   AC powered: false
             *   USB powered: true
             *   Wireless powered: false
             *   Max charging current: 0
             *   Max charging voltage: 0
             *   Charge counter: 471680
             *   status: 2
             *   health: 2
             *   present: true
             *   level: 67
             *   scale: 100
             *   voltage: 3960
             *   temperature: 303
             *   technology: Li-ion
             *   batterySWSelfDischarging: false
             *   batteryMiscEvent: 0
             *   batteryCurrentEvent: 131072
             *   mSecPlugTypeSummary: 2
             *   LED Charging: true
             *   LED Low Battery: true
             *   current now: 172
             *   charge counter: 471680
             *   Adaptive Fast Charging Settings: true
             *   USE_FAKE_BATTERY: false
             *   SEC_FEATURE_BATTERY_SIMULATION: false
             *   FEATURE_WIRELESS_FAST_CHARGER_CONTROL: false
             *   mWasUsedWirelessFastChargerPreviously: false
             *   mWirelessFastChargingSettingsEnable: true
             *   LLB CAL:
             *   LLB MAN: 20200521
             *   LLB CURRENT: YEAR2020M7D2
             *   LLB DIFF: 6
             *   FEATURE_HICCUP_CONTROL: true
             *   FEATURE_SUPPORTED_DAILY_BOARD: true
             *   SEC_FEATURE_USE_WIRELESS_POWER_SHARING: false
             *   BatteryInfoBackUp
             *   mSavedBatteryAsoc: -1
             *   mSavedBatteryMaxTemp: 412
             *   mSavedBatteryMaxCurrent: 3226
             *   mSavedBatteryUsage: 953
             *   FEATURE_SAVE_BATTERY_CYCLE: true
             *   SEC_FEATURE_PREVENT_SWELLING: true
             */
            try {
                val su = Runtime.getRuntime().exec("su")
                val outputStream = DataOutputStream(su.outputStream)
                outputStream.writeBytes("dumpsys battery\n")
                val input: InputStream = su.inputStream
                val reader = BufferedReader(InputStreamReader(input))

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    println("----> $line")
                }
                outputStream.flush()
                outputStream.writeBytes("exit\n")
                outputStream.flush()
                su.waitFor()
            } catch (e: IOException) {
                throw java.lang.Exception(e)
            } catch (e: InterruptedException) {
                throw java.lang.Exception(e)
            }
        }
        return isChargingStatus ?: mBatteryManager.isCharging
    }

    override fun getBatteryLevel(): Int {
        var batteryCapacity = mBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)
        if (batteryCapacity == 0 || batteryCapacity == Integer.MIN_VALUE)
            batteryCapacity = getBatteryLevelDirect()
        return batteryCapacity
    }

    private fun getBatteryLevelDirect(): Int {
        val capacityFile = File("$batteryDriverBaseDir/battery/capacity")
        if (capacityFile.exists() && capacityFile.isFile && capacityFile.canRead()) {
            val scanner = Scanner(capacityFile)
            if (scanner.hasNextInt()) {
                val probCapacity = scanner.nextInt()
                if (probCapacity in 1..100) {
                    return probCapacity
                }
            }
        }
        val ueventFile = File("$batteryDriverBaseDir/battery/uevent")
        if (ueventFile.exists() && ueventFile.isFile && ueventFile.canRead()) {
            val scanner = Scanner(ueventFile)
            do {
                val line = scanner.nextLine()
                if (line.contains("POWER_SUPPLY_CAPACITY")) {
                    val lineScanner = Scanner(line)
                    do {
                        val probCapacity = lineScanner.nextInt()
                        if (probCapacity in 1..100) {
                            return probCapacity
                        }
                    } while (lineScanner.hasNextInt())
                }
            } while (scanner.hasNextLine())
        }
        return -1
    }

    private fun readOnlineStatus(folder: String): Boolean {
        val onlineFile = File("$batteryDriverBaseDir/$folder/online")
        if (onlineFile.exists() && onlineFile.isFile && onlineFile.canRead()) {
            val scanner = Scanner(onlineFile)
            if (scanner.hasNextInt()) {
                val probStatus = scanner.nextInt()
                if (probStatus in 0..1) {
                    return when (probStatus) {
                        1 -> true
                        else -> false
                    }
                }
            }
        }
        val ueventFile = File("$batteryDriverBaseDir/$folder/uevent")
        if (ueventFile.exists() && ueventFile.isFile && ueventFile.canRead()) {
            val scanner = Scanner(ueventFile)
            do {
                val line = scanner.nextLine()
                if (line.contains("POWER_SUPPLY_ONLINE")) {
                    val lineScanner = Scanner(line)
                    do {
                        val probStatus = lineScanner.nextInt()
                        if (probStatus in 0..1) {
                            return when (probStatus) {
                                1 -> true
                                else -> false
                            }
                        }
                    } while (lineScanner.hasNextInt())
                }
            } while (scanner.hasNextLine())
        }
        return false
    }

    private fun getChargingAC(): Boolean {
        val acFolder = when {
            !File("$batteryDriverBaseDir/ac/").exists() &&
                    File("$batteryDriverBaseDir/pc_port/").exists() -> "usb"
            else -> "ac"
        }
        return readOnlineStatus(acFolder)
    }

    private fun getChargingUSB(): Boolean {
        val usbFolder = when {
            !File("$batteryDriverBaseDir/ac/").exists() &&
                    File("$batteryDriverBaseDir/pc_port/").exists() -> "pc_port"
            else -> "usb"
        }
        return readOnlineStatus(usbFolder)
    }

    private fun getChargingQi(): Boolean {
        val qiFileDir = File("$batteryDriverBaseDir/wireless/")
        if (!(qiFileDir.exists() && qiFileDir.isDirectory && qiFileDir.canRead()))
            return false
        return readOnlineStatus("wireless")
    }

    override fun getBatteryStatus(): BatteryStatus {
        return when {
            isCharging() -> when {
                getChargingAC() -> BatteryStatus.AC_CHARGING
                getChargingUSB() -> BatteryStatus.USB_CHARGING
                getChargingQi() -> BatteryStatus.QI_CHARGING
                else -> BatteryStatus.CHARGING
            }
            else -> BatteryStatus.DISCHARGING
        }
    }

    override fun getBatteryCurrentNow(): Int {
        val current = mBatteryManager.getIntProperty(BATTERY_PROPERTY_CURRENT_NOW)
        signal = if (!isCharging() && current > 0) -1 else 1
        if (abs(current) > 10000) scale = 100
        if (abs(current) > 100000) scale = 1000
        return (current * signal) / scale
    }


    override fun getBatteryRemainingEnergy(): Long {
        return mBatteryManager.getLongProperty(BATTERY_PROPERTY_ENERGY_COUNTER)
    }

    override fun getBatteryCharge(): Int {
        return mBatteryManager.getIntProperty(BATTERY_PROPERTY_CHARGE_COUNTER)
    }

    override fun getBatteryCapacity(): Int {
        return getBatteryCapacityReflection()
    }

    // https://android.googlesource.com/platform/frameworks/base/+/a029ea1/core/java/com/android/internal/os/PowerProfile.java
    @SuppressLint("PrivateApi")
    private fun getBatteryCapacityReflection(): Int {
        val mPowerProfile: Any
        var batteryCapacity = 0.0
        val powerProfileClass = "com.android.internal.os.PowerProfile"
        try {
            mPowerProfile = Class.forName(powerProfileClass)
                    .getConstructor(Context::class.java)
                    .newInstance(context)
            batteryCapacity = Class
                    .forName(powerProfileClass)
                    .getMethod("getBatteryCapacity")
                    .invoke(mPowerProfile) as Double
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return batteryCapacity.toInt()
    }

    @Suppress("unused")
    @SuppressLint("PrivateApi")
    /**
     * This function may become useful to read system avg consumption by state
     *
     * cpu.idle
     * cpu.awake
     * cpu.active
     * wifi.scan
     * wifi.on
     * wifi.active
     * gps.on
     * bluetooth.on
     * screen.on
     * screen.full
     */
    private fun getBatteryAvgPowerReflection(property: String): Int {
        val mPowerProfile: Any
        var batteryCapacity = 0.0
        val powerProfileClass = "com.android.internal.os.PowerProfile"
        try {
            mPowerProfile = Class.forName(powerProfileClass)
                    .getConstructor(Context::class.java)
                    .newInstance(context)
            batteryCapacity = Class
                    .forName(powerProfileClass)
                    .getMethod("getAveragePower", String::class.java)
                    .invoke(mPowerProfile, property) as Double
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return batteryCapacity.toInt()
    }

}