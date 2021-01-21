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

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.power

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.BatteryManager.*
import android.os.Build
import com.jaredrummler.android.device.DeviceName
import eu.chainfire.libsuperuser.Shell
import eu.chainfire.libsuperuser.Shell.OnSyncCommandLineListener
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger
import pt.up.fc.dcc.hyrax.jay.proto.JayProto
import pt.up.fc.dcc.hyrax.jay.proto.JayProto.PowerStatus
import java.io.File
import java.util.*
import java.util.concurrent.CountDownLatch
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

class AndroidPowerMonitor(private val context: Context) : PowerMonitor {
    private var lastIsChargingUpdateTime: Long = 0
    private val levelMonitor = BatteryLevelUpdatesReceiver()
    private val chargingStateMonitor = BatteryChargeStateUpdatesReceiver()
    private val batteryDriverBaseDir = "/sys/class/power_supply"
    private val mBatteryManager: BatteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private var scale = 1
    private var signal = 1
    private var deviceName: String = ""

    private var suAvailable: Boolean? = null

    override fun setCallbacks(_levelChangeCallback: (Int, Float, Float) -> Unit,
                              _statusChangeCallback: (PowerStatus) -> Unit) {
        statusChangeCallback = _statusChangeCallback
        levelChangeCallback = _levelChangeCallback
    }

    private companion object {
        var isChargingStatus: Boolean? = null
        var statusChangeCallback: ((PowerStatus) -> Unit)? = null
        var levelChangeCallback: ((Int, Float, Float) -> Unit)? = null
        var lastVoltage: Float? = null
    }

    class BatteryChargeStateUpdatesReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            var powerStatus: Intent? = null
            var status = 0
            if ((action == ACTION_POWER_CONNECTED) || (action == ACTION_POWER_DISCONNECTED)) {
                powerStatus = IntentFilter(ACTION_BATTERY_CHANGED).let { ifilter ->
                    context?.registerReceiver(null, ifilter)
                }
                status = powerStatus?.getIntExtra(EXTRA_STATUS, -1) ?: 0
            }
            isChargingStatus = false
            if (action == ACTION_POWER_CONNECTED) {
                isChargingStatus = true
                when (status) {
                    BATTERY_STATUS_FULL -> statusChangeCallback?.invoke(PowerStatus.FULL)
                    BATTERY_STATUS_CHARGING -> {
                        when (powerStatus?.getIntExtra(EXTRA_PLUGGED, -1)) {
                            BATTERY_PLUGGED_AC -> statusChangeCallback?.invoke(PowerStatus.AC_CHARGING)
                            BATTERY_PLUGGED_USB -> statusChangeCallback?.invoke(PowerStatus.USB_CHARGING)
                            BATTERY_PLUGGED_WIRELESS -> statusChangeCallback?.invoke(PowerStatus.QI_CHARGING)
                            else -> statusChangeCallback?.invoke(PowerStatus.CHARGING)
                        }
                    }
                }
            } else if ((action == ACTION_POWER_DISCONNECTED) && (status == BATTERY_STATUS_DISCHARGING)) {
                statusChangeCallback?.invoke(PowerStatus.DISCHARGING)
            } else if ((action == ACTION_POWER_DISCONNECTED)) {
                statusChangeCallback?.invoke(PowerStatus.DISCHARGING)
            }
        }
    }

    class BatteryLevelUpdatesReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action != ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(EXTRA_SCALE, -1)
            val voltage = intent.getIntExtra(EXTRA_VOLTAGE, -1) / 1000f
            lastVoltage = voltage
            val temperature = intent.getIntExtra(EXTRA_TEMPERATURE, -1) / 10f
            levelChangeCallback?.invoke(((level.toFloat() / scale.toFloat()) * 100f).roundToInt(), voltage, temperature)
        }
    }

    override fun monitor() {
        context.registerReceiver(levelMonitor, IntentFilter(ACTION_BATTERY_CHANGED))
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
        if (suAvailable == null)
            suAvailable = try {
                Shell.SU.available()
            } catch (ignore: Exception) {
                false
            }
        if (suAvailable == true && (System.currentTimeMillis() - lastIsChargingUpdateTime) > 10000) {
            lastIsChargingUpdateTime = System.currentTimeMillis()
            val countDownLatch = CountDownLatch(1)
            Shell.Pool.SU.run("dumpsys battery", object : OnSyncCommandLineListener {
                override fun onSTDOUT(line: String) {
                    val lowerLine = line.toLowerCase(Locale.ROOT)
                    if (lowerLine.contains("ac powered") && lowerLine.contains("true")) {
                        statusChangeCallback?.invoke(PowerStatus.AC_CHARGING)
                        isChargingStatus = true
                    } else if (lowerLine.contains("usb powered") && lowerLine.contains("true")) {
                        statusChangeCallback?.invoke(PowerStatus.USB_CHARGING)
                        isChargingStatus = true
                    } else if (lowerLine.contains("wireless powered") && lowerLine.contains("true")) {
                        statusChangeCallback?.invoke(PowerStatus.QI_CHARGING)
                        isChargingStatus = true
                    }
                    countDownLatch.countDown()
                }

                override fun onSTDERR(line: String) {
                    JayLogger.logWarn("SU_ERROR")
                    countDownLatch.countDown()
                }
            })
            countDownLatch.await()
        }
        return isChargingStatus ?: mBatteryManager.isCharging
    }

    override fun getLevel(): Int {
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

    override fun getStatus(): PowerStatus {
        return when {
            isCharging() -> when {
                getChargingAC() -> PowerStatus.AC_CHARGING
                getChargingUSB() -> PowerStatus.USB_CHARGING
                getChargingQi() -> PowerStatus.QI_CHARGING
                else -> PowerStatus.CHARGING
            }
            else -> PowerStatus.DISCHARGING
        }
    }


    override fun getFixedPowerEstimations(): JayProto.PowerEstimations {
        if (deviceName == "") {
            DeviceName.init(context)
            deviceName = DeviceName.getDeviceName(Build.DEVICE, Build.MODEL, "")
            JayLogger.logInfo("NEW_DEVICE_NAME")
        }
        JayLogger.logInfo("GET_FIX_POWERS", "", "NAME=$deviceName")
        return when (deviceName) {
            "Mi 9T" -> JayProto.PowerEstimations.newBuilder()
                    .setBatteryCapacity(getCapacity())
                    .setBatteryLevel(getLevel())
                    .setCompute(3.21f)
                    .setIdle(0.91f)
                    .setRx(1.07f)
                    .setTx(1.50f)
                    .build()
            "Galaxy S9+" -> JayProto.PowerEstimations.newBuilder()
                    .setBatteryCapacity(getCapacity())
                    .setBatteryLevel(getLevel())
                    .setCompute(3.66f)
                    .setIdle(0.89f)
                    .setRx(1.56f)
                    .setTx(3.14f)
                    .build()
            "Pixel 4" -> JayProto.PowerEstimations.newBuilder()
                    .setBatteryCapacity(getCapacity())
                    .setBatteryLevel(getLevel())
                    .setCompute(3.17f)
                    .setIdle(1.11f)
                    .setRx(1.38f)
                    .setTx(1.63f)
                    .build()
            "Nexus 9" -> JayProto.PowerEstimations.newBuilder()
                    .setBatteryCapacity(getCapacity())
                    .setBatteryLevel(getLevel())
                    .setCompute(6.83f)
                    .setIdle(2.60f)
                    .setRx(3.34f)
                    .setTx(2.86f)
                    .build()
            "Galaxy Tab S5e" -> JayProto.PowerEstimations.newBuilder()
                    .setBatteryCapacity(getCapacity())
                    .setBatteryLevel(getLevel())
                    .setCompute(4.45f)
                    .setIdle(1.90f)
                    .setRx(2.31f)
                    .setTx(3.13f)
                    .build()
            else -> JayProto.PowerEstimations.newBuilder()
                    .setBatteryCapacity(getCapacity())
                    .setBatteryLevel(getLevel())
                    .setCompute(getPower())
                    .setIdle(getPower())
                    .setRx(getPower())
                    .setTx(getPower())
                    .build()
        }
    }

    /**
     * Obtain the current from the battery in Ampere.
     *
     * We obtain the value in mAh (with or without a decimal point) and convert it to A before returning.
     */
    override fun getCurrentNow(): Float {
        val current = mBatteryManager.getIntProperty(BATTERY_PROPERTY_CURRENT_NOW)
        signal = if (!isCharging() && current > 0) -1 else 1
        if (abs(current) > 10000) scale = 100
        if (abs(current) > 100000) scale = 1000
        return ((current * signal) / scale) / 1000f
    }

    override fun getPower(): Float {
        return if (lastVoltage != null) getCurrentNow() * lastVoltage!! else 0f
    }


    override fun getRemainingEnergy(): Long {
        return mBatteryManager.getLongProperty(BATTERY_PROPERTY_ENERGY_COUNTER)
    }

    override fun getCharge(): Float {
        return mBatteryManager.getIntProperty(BATTERY_PROPERTY_CHARGE_COUNTER) / 1000f
    }


    /**
     * Return the battery factory capacity in Ampere-Hour.
     *
     * We obtain the value in mAmpere-Hour and convert it to Ah before returning
     */
    override fun getCapacity(): Float {
        return getBatteryCapacityReflection() / 1000f
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