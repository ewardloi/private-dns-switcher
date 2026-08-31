package com.alexloi.pdnsswitcher

enum class Mode(val storageKey: String, val label: String) {
    DNS_PROBE("dns_probe", "1. DNS query to server (IP)"),
    SYSTEM_RESOLVE("system_resolve", "2. System domain resolve"),
    PING("ping", "3. Ping IP address"),
    WIFI_SSID("wifi_ssid", "4. Wi-Fi network name");

    companion object {
        val DEFAULT = DNS_PROBE

        fun fromKey(key: String?): Mode = values().firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
