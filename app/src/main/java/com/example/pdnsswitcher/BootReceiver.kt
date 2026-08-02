package com.alexloi.privatednsswitcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.isMonitorEnabled(context)) {
            val serviceIntent = Intent(context, WifiMonitorService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
