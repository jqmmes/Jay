/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.sensors

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.nfc.NfcManager
import android.os.Build
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger


object AndroidSensorManager : SensorManager {
    private lateinit var context: Context

    fun setContext(context: Context) {
        this.context = context
    }

    override fun getActiveSensors(): Set<String> {
        val sensorSet = LinkedHashSet<String>()
        if (locationActive()) sensorSet.add("GPS")
        if (bluetoothActive()) sensorSet.add("BLUETOOTH")
        if (nfcActive()) sensorSet.add("NFC")
        return sensorSet.toSet()
    }

    private fun locationActive(): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager?.isLocationEnabled
            } else {
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } ?: false
        } catch (ignore: Exception) {
            return false
        }
    }

    private fun bluetoothActive(): Boolean {
        return try {
            val bt: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
            bt?.adapter?.isEnabled ?: false
        } catch (ignore: Exception) {
            JayLogger.logError("Failed to obtain BluetoothManager")
            false
        }
    }

    private fun nfcActive(): Boolean {
        return try {
            val nfc: NfcManager? = context.getSystemService(Context.NFC_SERVICE) as NfcManager?
            nfc?.defaultAdapter?.isEnabled ?: false
        } catch (ignore: Exception) {
            JayLogger.logError("Failed to obtain NfcManager")
            false
        }
    }
}