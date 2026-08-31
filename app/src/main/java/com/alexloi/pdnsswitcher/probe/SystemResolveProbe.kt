package com.alexloi.pdnsswitcher.probe

import android.net.Network
import android.util.Log
import java.net.InetAddress

object SystemResolveProbe {

    private const val TAG = "SystemResolveProbe"

    fun resolves(domain: String, network: Network?): Boolean {
        if (domain.isBlank()) return false
        return try {
            if (network != null) {
                network.getAllByName(domain)
            } else {
                InetAddress.getAllByName(domain)
            }
            true
        } catch (e: Exception) {
            Log.i(TAG, "Resolve of $domain failed: ${e.message}")
            false
        }
    }
}
