package com.alexloi.pdnsswitcher

import android.content.Context
import android.net.Network
import com.alexloi.pdnsswitcher.probe.DnsProbe
import com.alexloi.pdnsswitcher.probe.PingProbe
import com.alexloi.pdnsswitcher.probe.SystemResolveProbe
import com.alexloi.pdnsswitcher.probe.WifiSsidChecker
import kotlinx.coroutines.delay

data class EvalResult(val local: Boolean, val detail: String)

object ModeEvaluator {

    private const val RETRY_DELAY_MS = 800L

    suspend fun evaluate(context: Context, network: Network?): EvalResult {
        val mode = Prefs.getMode(context)
        val retries = Prefs.getRetryCount(context).coerceAtLeast(1)

        var lastOk = false
        var lastDetail = ""
        var attemptsUsed = 0

        for (attempt in 1..retries) {
            attemptsUsed = attempt
            val (ok, detail) = runCheck(context, mode, network)
            lastOk = ok
            lastDetail = detail
            if (ok) break
            if (attempt < retries) delay(RETRY_DELAY_MS)
        }

        val suffix = if (retries > 1) {
            if (lastOk) " [succeeded on attempt $attemptsUsed/$retries]" else " [failed after $retries attempts]"
        } else ""
        return EvalResult(lastOk, lastDetail + suffix)
    }

    private suspend fun runCheck(context: Context, mode: Mode, network: Network?): Pair<Boolean, String> {
        return when (mode) {

            Mode.DNS_PROBE -> {
                val ip = Prefs.getTestIp(context)
                val domain = Prefs.getProbeDomain(context)
                val ok = DnsProbe.probe(ip, domain, network)
                ok to "DNS probe to $ip (domain $domain): ${if (ok) "responded" else "no response"}"
            }

            Mode.SYSTEM_RESOLVE -> {
                val domain = Prefs.getProbeDomain(context)
                PrivateDnsManager.setAutomatic(context)
                delay(300)
                val ok = SystemResolveProbe.resolves(domain, network)
                ok to "System resolve of $domain: ${if (ok) "succeeded (local network)" else "failed"}"
            }

            Mode.PING -> {
                val ip = Prefs.getTestIp(context)
                val ok = PingProbe.ping(context, ip, network)
                ok to "Ping $ip: ${if (ok) "reachable" else "unreachable"}"
            }

            Mode.WIFI_SSID -> {
                val ssid = WifiSsidChecker.currentSsid(context, network)
                val trusted = Prefs.getSsidList(context)
                val matched = ssid != null && trusted.any { it.equals(ssid, ignoreCase = true) }
                matched to "Wi-Fi SSID '${ssid ?: "unknown"}': ${if (matched) "trusted" else "not trusted"}"
            }
        }
    }
}
