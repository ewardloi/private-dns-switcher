package com.alexloi.pdnsswitcher.probe

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit

object PingProbe {

    private const val TAG = "PingProbe"

    fun ping(context: Context, ip: String, network: Network?, timeoutSeconds: Int = 2): Boolean {
        val cleanIp = ip.trim().removeSurrounding("[", "]")
        if (cleanIp.isBlank()) return false

        val iface = interfaceName(context, network)
        val isIpv6 = cleanIp.contains(":")
        val candidates = candidateCommands(cleanIp, iface, isIpv6, timeoutSeconds)

        for (cmd in candidates) {
            var process: Process? = null
            try {
                process = ProcessBuilder(cmd).redirectErrorStream(true).start()
                val finished = process.waitFor(timeoutSeconds + 3L, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroy()
                    return false
                }
                return process.exitValue() == 0
            } catch (e: IOException) {
                Log.i(TAG, "Ping command ${cmd.firstOrNull()} unavailable: ${e.message}")
            } catch (e: Exception) {
                Log.i(TAG, "Ping to $cleanIp failed: ${e.message}")
                return false
            } finally {
                process?.destroy()
            }
        }
        return false
    }

    private fun candidateCommands(
        ip: String,
        iface: String?,
        isIpv6: Boolean,
        timeoutSeconds: Int
    ): List<List<String>> {
        fun build(binary: String, extraFlag: String?): List<String> {
            val cmd = mutableListOf(binary, "-c", "1", "-W", timeoutSeconds.toString())
            extraFlag?.let { cmd.add(it) }
            iface?.let {
                cmd.add("-I")
                cmd.add(it)
            }
            cmd.add(ip)
            return cmd
        }

        return if (isIpv6) {
            listOf(
                build("/system/bin/ping6", null),
                build("/system/bin/ping", "-6")
            )
        } else {
            listOf(build("/system/bin/ping", null))
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
