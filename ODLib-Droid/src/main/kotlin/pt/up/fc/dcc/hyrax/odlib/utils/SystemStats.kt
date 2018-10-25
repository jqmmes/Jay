package pt.up.fc.dcc.hyrax.odlib.utils

import android.content.Context
import android.os.BatteryManager
import java.io.IOException
import java.io.RandomAccessFile
import android.content.Context.BATTERY_SERVICE
import android.os.Build
import android.support.annotation.RequiresApi
import pt.up.fc.dcc.hyrax.odlib.status.StatusManager
import kotlin.concurrent.thread


object SystemStats {

    fun getCpuCount(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    /*fun printCpuUsages() {
        try {
            val reader = RandomAccessFile("/proc/stat", "r")
            var load = reader.readLine()
            while (load != null) {
                ODLogger.logInfo("CPU usage: $load")
                load = reader.readLine()
            }
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
    }*/

    fun getBatteryCharging(context: Context): Boolean {
        /*val bm = context.getSystemService(BATTERY_SERVICE) as BatteryManager?
        ODLogger.logInfo(bm!!.getIntProperty(BatteryManager.BATTERY_STATUS_CHARGING).toString())
        ODLogger.logInfo(bm!!.getIntProperty(BatteryManager.BATTERY_PLUGGED_USB).toString())
        ODLogger.logInfo(bm!!.getIntProperty(BatteryManager.BATTERY_PLUGGED_WIRELESS).toString())
        ODLogger.logInfo(bm!!.getIntProperty(BatteryManager.BATTERY_PLUGGED_AC).toString())
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
}