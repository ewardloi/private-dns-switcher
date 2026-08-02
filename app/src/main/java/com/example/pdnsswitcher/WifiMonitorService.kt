package com.alexloi.privatednsswitcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WifiMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Wi-Fi monitoring started"))
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
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Bind the probe to this specific Wi-Fi network
                handleEvent("Wi-Fi connected", network)
            }

            override fun onLost(network: Network) {
                // The network is already gone - probe over whatever network is
                // currently active (could be mobile data, or none at all)
                handleEvent("Wi-Fi disconnected", null)
            }
        }
        connectivityManager.registerNetworkCallback(request, callback!!)
    }

    private fun handleEvent(reason: String, network: Network?) {
        scope.launch {
            val delaySeconds = Prefs.getProbeDelaySeconds(applicationContext)
            // let the network settle before probing
            delay(delaySeconds * 1000L)

            val testIp = Prefs.getTestIp(applicationContext)
            val hostname = Prefs.getHostname(applicationContext)
            val probeDomain = Prefs.getProbeDomain(applicationContext)

            val resolved = DnsProbe.probe(testIp, probeDomain, network)

            val ok: Boolean
            val statusLine: String
            if (resolved) {
                ok = PrivateDnsManager.setAutomatic(applicationContext)
                statusLine = "$reason: $testIp reachable -> Private DNS = Automatic"
            } else {
                ok = PrivateDnsManager.setHostname(applicationContext, hostname)
                statusLine = "$reason: $testIp unreachable -> Private DNS = $hostname"
            }

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
