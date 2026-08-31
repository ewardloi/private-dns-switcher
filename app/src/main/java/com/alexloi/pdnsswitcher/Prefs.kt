package com.alexloi.pdnsswitcher

import android.content.Context

object Prefs {
    private const val NAME = "pdns_prefs"
    private const val KEY_IP = "test_ip"
    private const val KEY_HOST = "hostname"
    private const val KEY_ENABLED = "monitor_enabled"
    private const val KEY_DELAY_SEC = "probe_delay_sec"
    private const val KEY_PROBE_DOMAIN = "probe_domain"
    private const val KEY_MODE = "mode"
    private const val KEY_SSID_LIST = "ssid_list"

    private const val DEFAULT_IP = "10.10.1.1"
    private const val DEFAULT_DELAY_SEC = 2
    private const val DEFAULT_PROBE_DOMAIN = "google.com"

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getTestIp(context: Context): String =
        prefs(context).getString(KEY_IP, DEFAULT_IP) ?: DEFAULT_IP

    fun getHostname(context: Context): String =
        prefs(context).getString(KEY_HOST, "") ?: ""

    fun getProbeDelaySeconds(context: Context): Int =
        prefs(context).getInt(KEY_DELAY_SEC, DEFAULT_DELAY_SEC)

    fun getProbeDomain(context: Context): String =
        prefs(context).getString(KEY_PROBE_DOMAIN, DEFAULT_PROBE_DOMAIN) ?: DEFAULT_PROBE_DOMAIN

    fun getSsidListRaw(context: Context): String =
        prefs(context).getString(KEY_SSID_LIST, "") ?: ""

    fun getSsidList(context: Context): List<String> =
        getSsidListRaw(context)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun setValues(
        context: Context,
        ip: String,
        hostname: String,
        delaySeconds: Int,
        probeDomain: String,
        ssidListRaw: String
    ) {
        prefs(context).edit()
            .putString(KEY_IP, ip)
            .putString(KEY_HOST, hostname)
            .putInt(KEY_DELAY_SEC, delaySeconds)
            .putString(KEY_PROBE_DOMAIN, probeDomain)
            .putString(KEY_SSID_LIST, ssidListRaw)
            .apply()
    }

    fun getMode(context: Context): Mode = Mode.fromKey(prefs(context).getString(KEY_MODE, null))

    fun setMode(context: Context, mode: Mode) {
        prefs(context).edit().putString(KEY_MODE, mode.storageKey).apply()
    }

    fun isMonitorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setMonitorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
