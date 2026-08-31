package com.alexloi.pdnsswitcher.probe

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import java.util.concurrent.TimeUnit

object PingProbe {

    private const val TAG = "PingProbe"

    fun ping(context: Context, ip: String, network: Network?, timeoutSeconds: Int = 2): Boolean {
        if (ip.isBlank()) return false
        var process: Process? = null
        return try {
            val cmd = mutableListOf("/system/bin/ping", "-c", "1", "-W", timeoutSeconds.toString())
            interfaceName(context, network)?.let { iface ->
                cmd.add("-I")
                cmd.add(iface)
            }
            cmd.add(ip)

            process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val finished = process.waitFor(timeoutSeconds + 3L, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                return false
            }
            process.exitValue() == 0
        } catch (e: Exception) {
            Log.i(TAG, "Ping to $ip failed: ${e.message}")
            false
        } finally {
            process?.destroy()
        }
    }

    private fun interfaceName(context: Context, network: Network?): String? {
        if (network == null) return null
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.getLinkProperties(network)?.interfaceName
        } catch (e: Exception) {
            null
        }
    }
}
