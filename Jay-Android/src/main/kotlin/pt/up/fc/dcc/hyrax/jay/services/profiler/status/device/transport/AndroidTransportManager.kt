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

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.transport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import pt.up.fc.dcc.hyrax.jay.logger.JayLogger

object AndroidTransportManager : TransportManager {
    private lateinit var context: Context

    fun setContext(context: Context) {
        this.context = context
    }

    override fun getTransport(): TransportInfo {
        if (!this::context.isInitialized) throw AssertionError("Must setContext before getTransport")
        try {
            val cm = this.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val nc = cm.getNetworkCapabilities(cm.activeNetwork)!!
            val tm = this.context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            var nt: CellularTechnology? = null
            // Check whether we are using Wi-Fi transport
            val transport = when {
                nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TransportMedium.WIFI
                nc.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> TransportMedium.BLUETOOTH
                nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> TransportMedium.VPN
                nc.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> TransportMedium.BLUETOOTH
                nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TransportMedium.ETHERNET
                nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    nt = if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.READ_PHONE_STATE) ==
                            PackageManager.PERMISSION_GRANTED) {
                        when (tm.dataNetworkType) {
                            TelephonyManager.NETWORK_TYPE_GSM or TelephonyManager.NETWORK_TYPE_EDGE or TelephonyManager
                                    .NETWORK_TYPE_GPRS or TelephonyManager.NETWORK_TYPE_CDMA -> CellularTechnology.SECOND_GEN // 2G
                            TelephonyManager.NETWORK_TYPE_UMTS or TelephonyManager.NETWORK_TYPE_HSDPA or
                                    TelephonyManager.NETWORK_TYPE_HSUPA or TelephonyManager.NETWORK_TYPE_HSPA or
                                    TelephonyManager.NETWORK_TYPE_HSPAP or TelephonyManager.NETWORK_TYPE_EVDO_0 or
                                    TelephonyManager.NETWORK_TYPE_EVDO_A or TelephonyManager.NETWORK_TYPE_EVDO_B or
                                    TelephonyManager.NETWORK_TYPE_TD_SCDMA -> CellularTechnology.THIRD_GEN
                            TelephonyManager.NETWORK_TYPE_LTE -> CellularTechnology.FOURTH_GEN  // 4G
                            TelephonyManager.NETWORK_TYPE_NR -> CellularTechnology.FIFTH_GEN // API29 (Android 10)
                            else -> CellularTechnology.UNKNOWN_GEN
                        }
                    } else {
                        JayLogger.logError("GET_TRANSPORT", "", "MISSING READ_PHONE_STATE PERMISSIONS")
                        CellularTechnology.UNKNOWN_GEN
                    }
                    TransportMedium.CELLULAR
                }
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) -> TransportMedium.WIFI_AWARE
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                        nc.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) -> TransportMedium.LOWPAN
                else -> {
                    TransportMedium.UNKNOWN
                }
            }

            return TransportInfo(transport, nc.linkUpstreamBandwidthKbps, nc.linkDownstreamBandwidthKbps, nt)
        } catch (ignore: Exception) {
            ignore.printStackTrace()
            return TransportInfo(TransportMedium.WIFI, 10000, 10000, null)
        }
    }
}

