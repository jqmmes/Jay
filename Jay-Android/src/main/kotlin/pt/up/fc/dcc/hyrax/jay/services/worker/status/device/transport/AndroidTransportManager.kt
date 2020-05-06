package pt.up.fc.dcc.hyrax.jay.services.worker.status.device.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager

object AndroidTransportManager : TransportManager {
    private lateinit var context: Context

    fun setContext(context: Context) {
        this.context = context
    }

    override fun getTransport(): TransportInfo {
        if (!this::context.isInitialized) throw AssertionError("Must setContext before getTransport")
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
                nt = when (tm.networkType) {
                    TelephonyManager.NETWORK_TYPE_GSM or TelephonyManager.NETWORK_TYPE_EDGE or TelephonyManager
                            .NETWORK_TYPE_GPRS or TelephonyManager.NETWORK_TYPE_CDMA -> CellularTechnology.SECOND_GEN // 2G
                    TelephonyManager.NETWORK_TYPE_UMTS or TelephonyManager.NETWORK_TYPE_HSDPA or
                            TelephonyManager.NETWORK_TYPE_HSUPA or TelephonyManager.NETWORK_TYPE_HSPA or
                            TelephonyManager.NETWORK_TYPE_HSPAP or TelephonyManager.NETWORK_TYPE_EVDO_0 or
                            TelephonyManager.NETWORK_TYPE_EVDO_A or TelephonyManager.NETWORK_TYPE_EVDO_B or
                            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> CellularTechnology.THIRD_GEN
                    TelephonyManager.NETWORK_TYPE_LTE -> CellularTechnology.FOURTH_GEN  // 4G
                    TelephonyManager.NETWORK_TYPE_NR -> CellularTechnology.FIFTH_GEN // API29 (Android 10)
                    else -> CellularTechnology.UNKNOWN
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
    }
}

