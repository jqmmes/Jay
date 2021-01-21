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