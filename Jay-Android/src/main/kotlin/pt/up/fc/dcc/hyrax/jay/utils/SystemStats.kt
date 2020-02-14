
package pt.up.fc.dcc.hyrax.jay.utils

import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.os.BatteryManager
import android.os.BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER


object SystemStats {

    fun getCpuCount(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    /*fun printCpuUsages() {
        try {
            val reader = RandomAccessFile("/proc/stat", "r")
            var load = reader.readLine()
            while (load != null) {
                JayLogger.logInfo("CPU usage: $load")
                load = reader.readLine()
            }
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
    }*/

    fun getBatteryCharging(@Suppress("UNUSED_PARAMETER") context: Context): Boolean {
        /*val bm = context.getSystemService(BATTERY_SERVICE) as BatteryManager?
        JayLogger.logInfo(bm!!.getIntProperty(BatteryManager.BATTERY_STATUS_CHARGING).toString())
        JayLogger.logInfo(bm!!.getIntProperty(BatteryManager.BATTERY_PLUGGED_USB).toString())
        JayLogger.logInfo(bm!!.getIntProperty(BatteryManager.BATTERY_PLUGGED_WIRELESS).toString())
        JayLogger.logInfo(bm!!.getIntProperty(BatteryManager.BATTERY_PLUGGED_AC).toString())
        if (bm!!.getIntProperty(BatteryManager.BATTERY_STATUS_CHARGING) == 1
        || bm.getIntProperty(BatteryManager.BATTERY_PLUGGED_USB) == 1
        || bm.getIntProperty(BatteryManager.BATTERY_PLUGGED_WIRELESS) == 1
        || bm.getIntProperty(BatteryManager.BATTERY_PLUGGED_AC) == 1) {
            return true
        }*/
        return false
        // return bm!!.isCharging // Only works in Android M or newer
    }

    fun getBatteryPercentage(context: Context): Int {
        val bm = context.getSystemService(BATTERY_SERVICE) as BatteryManager?
        return bm!!.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun getBatteryEnergyCounter(context: Context): Long {
        val bm = context.getSystemService(BATTERY_SERVICE) as BatteryManager?
        return bm!!.getLongProperty(BATTERY_PROPERTY_ENERGY_COUNTER)
    }
}