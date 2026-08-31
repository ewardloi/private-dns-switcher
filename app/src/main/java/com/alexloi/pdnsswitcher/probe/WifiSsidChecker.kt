package com.alexloi.pdnsswitcher.probe

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

object WifiSsidChecker {

    private const val TAG = "WifiSsidChecker"

    fun currentSsid(context: Context, network: Network?): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val target = network ?: cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(target) ?: return null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

            val fromCaps: WifiInfo? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.transportInfo as? WifiInfo else null
            val wifiInfo: WifiInfo? = fromCaps
                ?: (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo

            val raw = wifiInfo?.ssid?.trim('"')
            if (raw.isNullOrBlank() || raw == "<unknown ssid>") null else raw
        } catch (e: Exception) {
            Log.i(TAG, "Failed to read SSID: ${e.message}")
            null
        }
    }
}
