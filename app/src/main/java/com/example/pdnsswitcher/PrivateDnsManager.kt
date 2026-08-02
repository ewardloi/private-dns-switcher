package com.alexloi.privatednsswitcher

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

object PrivateDnsManager {
    private const val TAG = "PrivateDnsManager"

    private const val MODE_AUTO = "opportunistic" // this is the "Automatic" mode in the Android UI
    private const val MODE_HOSTNAME = "hostname"

    fun setAutomatic(context: Context): Boolean = try {
        Settings.Global.putString(context.contentResolver, "private_dns_mode", MODE_AUTO)
        Log.i(TAG, "Private DNS -> automatic (opportunistic)")
        true
    } catch (e: SecurityException) {
        Log.e(TAG, "No WRITE_SECURE_SETTINGS permission: ${e.message}")
        false
    }

    fun setHostname(context: Context, hostname: String): Boolean {
        if (hostname.isBlank()) {
            Log.e(TAG, "Hostname is empty, skipping")
            return false
        }
        return try {
            Settings.Global.putString(context.contentResolver, "private_dns_mode", MODE_HOSTNAME)
            Settings.Global.putString(context.contentResolver, "private_dns_specifier", hostname)
            Log.i(TAG, "Private DNS -> hostname: $hostname")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "No WRITE_SECURE_SETTINGS permission: ${e.message}")
            false
        }
    }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
}
