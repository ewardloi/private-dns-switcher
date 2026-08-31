package com.alexloi.pdnsswitcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastTransportSignature: String? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Network monitoring started"))
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        registerCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        callback?.let { connectivityManager.unregisterNetworkCallback(it) }
        scope.cancel()
        super.onDestroy()
    }

    private fun registerCallback() {
        callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                handleEvent("Network connected", network)
            }

            override fun onLost(network: Network) {
                lastTransportSignature = null
                handleEvent("Network disconnected", null)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val signature = transportSignature(caps)
                val previous = lastTransportSignature
                lastTransportSignature = signature
                if (previous != null && previous != signature) {
                    handleEvent("Network type changed ($signature)", network)
                }
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback!!)
    }

    private fun transportSignature(caps: NetworkCapabilities): String {
        val parts = mutableListOf<String>()
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) parts += "wifi"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) parts += "cellular"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) parts += "ethernet"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) parts += "vpn"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) parts += "bluetooth"
        return parts.sorted().joinToString("+").ifEmpty { "none" }
    }

    private fun handleEvent(reason: String, network: Network?) {
        scope.launch {
            val delaySeconds = Prefs.getProbeDelaySeconds(applicationContext)
            delay(delaySeconds * 1000L)

            val result = ModeEvaluator.evaluate(applicationContext, network)
            val hostname = Prefs.getHostname(applicationContext)

            val ok = if (result.local) {
                PrivateDnsManager.setAutomatic(applicationContext)
            } else {
                PrivateDnsManager.setHostname(applicationContext, hostname)
            }

            val modeLabel = Prefs.getMode(applicationContext).label
            val privateDnsState = if (result.local) "Automatic" else hostname
            val statusLine = "$reason [$modeLabel]\n${result.detail}\nPrivate DNS = $privateDnsState"

            updateNotification(if (ok) statusLine else "$statusLine\n(missing WRITE_SECURE_SETTINGS permission)")
        }
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "pdns_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Private DNS Switcher",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Private DNS Switcher")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val NOTIFICATION_ID = 42
    }
}
