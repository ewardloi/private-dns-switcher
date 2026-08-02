package com.alexloi.privatednsswitcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var ipInput: EditText
    private lateinit var hostInput: EditText
    private lateinit var delayInput: EditText
    private lateinit var domainInput: EditText
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ipInput = findViewById(R.id.ipInput)
        hostInput = findViewById(R.id.hostInput)
        delayInput = findViewById(R.id.delayInput)
        domainInput = findViewById(R.id.domainInput)
        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        val saveButton: Button = findViewById(R.id.saveButton)
        val permissionButton: Button = findViewById(R.id.permissionButton)

        ipInput.setText(Prefs.getTestIp(this))
        hostInput.setText(Prefs.getHostname(this))
        delayInput.setText(Prefs.getProbeDelaySeconds(this).toString())
        domainInput.setText(Prefs.getProbeDomain(this))

        saveButton.setOnClickListener {
            saveCurrentValues()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        permissionButton.setOnClickListener { showPermissionInstructions() }

        toggleButton.setOnClickListener {
            if (Prefs.isMonitorEnabled(this)) {
                stopService(Intent(this, WifiMonitorService::class.java))
                Prefs.setMonitorEnabled(this, false)
            } else {
                if (!PrivateDnsManager.hasPermission(this)) {
                    showPermissionInstructions()
                    return@setOnClickListener
                }
                saveCurrentValues()
                ContextCompat.startForegroundService(this, Intent(this, WifiMonitorService::class.java))
                Prefs.setMonitorEnabled(this, true)
            }
            updateStatus()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun saveCurrentValues() {
        val delaySeconds = delayInput.text.toString().trim().toIntOrNull() ?: 2
        val domain = domainInput.text.toString().trim().ifEmpty { "google.com" }
        Prefs.setValues(
            this,
            ipInput.text.toString().trim(),
            hostInput.text.toString().trim(),
            delaySeconds,
            domain
        )
    }

    private fun updateStatus() {
        val running = Prefs.isMonitorEnabled(this)
        toggleButton.text = if (running) "Stop monitoring" else "Start monitoring"
        val perm = if (PrivateDnsManager.hasPermission(this)) "permission granted" else "permission NOT granted"
        statusText.text = "Status: ${if (running) "running" else "stopped"}\nWRITE_SECURE_SETTINGS: $perm"
    }

    private fun showPermissionInstructions() {
        AlertDialog.Builder(this)
            .setTitle("WRITE_SECURE_SETTINGS permission required")
            .setMessage(
                "Android does not let regular apps modify system settings " +
                    "directly. Run this once on a computer with the phone connected " +
                    "via USB (USB debugging enabled, adb installed):\n\n" +
                    "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n\n" +
                    "The permission will persist until the app is uninstalled."
            )
            .setPositiveButton("Got it", null)
            .show()
    }
}
