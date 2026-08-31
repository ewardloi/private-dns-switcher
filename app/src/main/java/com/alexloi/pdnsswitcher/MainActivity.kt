package com.alexloi.pdnsswitcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var modeRadioGroup: RadioGroup
    private lateinit var modeExplanationText: TextView
    private lateinit var sectionIp: View
    private lateinit var sectionDomain: View
    private lateinit var sectionSsid: View

    private lateinit var ipInput: EditText
    private lateinit var hostInput: EditText
    private lateinit var delayInput: EditText
    private lateinit var domainInput: EditText
    private lateinit var ssidInput: EditText
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var locationPermissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        modeRadioGroup = findViewById(R.id.modeRadioGroup)
        modeExplanationText = findViewById(R.id.modeExplanationText)
        sectionIp = findViewById(R.id.sectionIp)
        sectionDomain = findViewById(R.id.sectionDomain)
        sectionSsid = findViewById(R.id.sectionSsid)

        ipInput = findViewById(R.id.ipInput)
        hostInput = findViewById(R.id.hostInput)
        delayInput = findViewById(R.id.delayInput)
        domainInput = findViewById(R.id.domainInput)
        ssidInput = findViewById(R.id.ssidInput)
        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        locationPermissionButton = findViewById(R.id.locationPermissionButton)
        val saveButton: Button = findViewById(R.id.saveButton)
        val permissionButton: Button = findViewById(R.id.permissionButton)

        ipInput.setText(Prefs.getTestIp(this))
        hostInput.setText(Prefs.getHostname(this))
        delayInput.setText(Prefs.getProbeDelaySeconds(this).toString())
        domainInput.setText(Prefs.getProbeDomain(this))
        ssidInput.setText(Prefs.getSsidListRaw(this))

        modeRadioGroup.check(modeToRadioId(Prefs.getMode(this)))
        updateModeUi(Prefs.getMode(this))

        modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = radioIdToMode(checkedId)
            Prefs.setMode(this, mode)
            updateModeUi(mode)
        }

        saveButton.setOnClickListener {
            saveCurrentValues()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        permissionButton.setOnClickListener { showPermissionInstructions() }

        locationPermissionButton.setOnClickListener {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION
            )
        }

        toggleButton.setOnClickListener {
            if (Prefs.isMonitorEnabled(this)) {
                stopService(Intent(this, NetworkMonitorService::class.java))
                Prefs.setMonitorEnabled(this, false)
            } else {
                if (!PrivateDnsManager.hasPermission(this)) {
                    showPermissionInstructions()
                    return@setOnClickListener
                }
                if (Prefs.getMode(this) == Mode.WIFI_SSID && !hasLocationPermission()) {
                    Toast.makeText(
                        this,
                        "Mode 4 requires location permission",
                        Toast.LENGTH_LONG
                    ).show()
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION
                    )
                    return@setOnClickListener
                }
                saveCurrentValues()
                ContextCompat.startForegroundService(this, Intent(this, NetworkMonitorService::class.java))
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

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun radioIdToMode(checkedId: Int): Mode = when (checkedId) {
        R.id.radioDnsProbe -> Mode.DNS_PROBE
        R.id.radioSystemResolve -> Mode.SYSTEM_RESOLVE
        R.id.radioPing -> Mode.PING
        R.id.radioWifiSsid -> Mode.WIFI_SSID
        else -> Mode.DEFAULT
    }

    private fun modeToRadioId(mode: Mode): Int = when (mode) {
        Mode.DNS_PROBE -> R.id.radioDnsProbe
        Mode.SYSTEM_RESOLVE -> R.id.radioSystemResolve
        Mode.PING -> R.id.radioPing
        Mode.WIFI_SSID -> R.id.radioWifiSsid
    }

    private fun updateModeUi(mode: Mode) {
        sectionIp.visibility = if (mode == Mode.DNS_PROBE || mode == Mode.PING) View.VISIBLE else View.GONE
        sectionDomain.visibility = if (mode == Mode.DNS_PROBE || mode == Mode.SYSTEM_RESOLVE) View.VISIBLE else View.GONE
        sectionSsid.visibility = if (mode == Mode.WIFI_SSID) View.VISIBLE else View.GONE

        modeExplanationText.text = when (mode) {
            Mode.DNS_PROBE ->
                "Sends a DNS query directly to the IP. Responds -> local network, Private DNS turns off. No response -> it turns on."
            Mode.SYSTEM_RESOLVE ->
                "Turns Private DNS off, then tries to resolve the domain with the normal system resolver. Resolves -> we are on the local network, stays off. Fails -> Private DNS turns on."
            Mode.PING ->
                "Pings the IP. Reachable -> local network, Private DNS turns off. Unreachable -> it turns on."
            Mode.WIFI_SSID ->
                "Compares the connected Wi-Fi network name against the trusted list. Match -> Private DNS turns off. No match -> it turns on."
        }
    }

    private fun saveCurrentValues() {
        val delaySeconds = delayInput.text.toString().trim().toIntOrNull() ?: 2
        val domain = domainInput.text.toString().trim().ifEmpty { "google.com" }
        Prefs.setValues(
            this,
            ipInput.text.toString().trim(),
            hostInput.text.toString().trim(),
            delaySeconds,
            domain,
            ssidInput.text.toString().trim()
        )
    }

    private fun updateStatus() {
        val running = Prefs.isMonitorEnabled(this)
        toggleButton.text = if (running) "Stop monitoring" else "Start monitoring"
        val perm = if (PrivateDnsManager.hasPermission(this)) "permission granted" else "permission NOT granted"
        val locPerm = if (hasLocationPermission()) "granted" else "not granted"
        statusText.text = "Status: ${if (running) "running" else "stopped"}\n" +
            "Mode: ${Prefs.getMode(this).label}\n" +
            "WRITE_SECURE_SETTINGS: $perm\n" +
            "Location (for mode 4): $locPerm"
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

    companion object {
        private const val REQUEST_LOCATION = 2
    }
}
