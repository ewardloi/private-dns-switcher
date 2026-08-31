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

    suspend fun evaluate(context: Context, network: Network?): EvalResult {
        return when (Prefs.getMode(context)) {

            Mode.DNS_PROBE -> {
                val ip = Prefs.getTestIp(context)
                val domain = Prefs.getProbeDomain(context)
                val ok = DnsProbe.probe(ip, domain, network)
                EvalResult(ok, "DNS probe to $ip (domain $domain): ${if (ok) "responded" else "no response"}")
            }

            Mode.SYSTEM_RESOLVE -> {
                val domain = Prefs.getProbeDomain(context)
                PrivateDnsManager.setAutomatic(context)
                delay(300)
                val ok = SystemResolveProbe.resolves(domain, network)
                EvalResult(ok, "System resolve of $domain: ${if (ok) "succeeded (local network)" else "failed"}")
            }

            Mode.PING -> {
                val ip = Prefs.getTestIp(context)
                val ok = PingProbe.ping(context, ip, network)
                EvalResult(ok, "Ping $ip: ${if (ok) "reachable" else "unreachable"}")
            }

            Mode.WIFI_SSID -> {
                val ssid = WifiSsidChecker.currentSsid(context, network)
                val trusted = Prefs.getSsidList(context)
                val matched = ssid != null && trusted.any { it.equals(ssid, ignoreCase = true) }
                EvalResult(
                    matched,
                    "Wi-Fi SSID '${ssid ?: "unknown"}': ${if (matched) "trusted" else "not trusted"}"
                )
            }
        }
    }
}
