package com.alexloi.pdnsswitcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var modeRadioGroup: RadioGroup
    private lateinit var modeExplanationText: TextView
    private lateinit var sectionIp: View
    private lateinit var sectionDomain: View
    private lateinit var sectionSsid: View

    private lateinit var ipInput: EditText
    private lateinit var hostInput: EditText
    private lateinit var delayInput: EditText
    private lateinit var retryInput: EditText
    private lateinit var domainInput: EditText
    private lateinit var ssidInput: EditText

    private lateinit var statusCard: MaterialCardView
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView
    private lateinit var chipSecureSettings: Chip
    private lateinit var chipLocation: Chip
    private lateinit var toggleButton: MaterialButton
    private lateinit var locationPermissionButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val scrollView: View = findViewById(R.id.scrollView)
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }

        modeRadioGroup = findViewById(R.id.modeRadioGroup)
        modeExplanationText = findViewById(R.id.modeExplanationText)
        sectionIp = findViewById(R.id.sectionIp)
        sectionDomain = findViewById(R.id.sectionDomain)
        sectionSsid = findViewById(R.id.sectionSsid)

        ipInput = findViewById(R.id.ipInput)
        hostInput = findViewById(R.id.hostInput)
        delayInput = findViewById(R.id.delayInput)
        retryInput = findViewById(R.id.retryInput)
        domainInput = findViewById(R.id.domainInput)
        ssidInput = findViewById(R.id.ssidInput)

        statusCard = findViewById(R.id.statusCard)
        statusTitle = findViewById(R.id.statusTitle)
        statusSubtitle = findViewById(R.id.statusSubtitle)
        chipSecureSettings = findViewById(R.id.chipSecureSettings)
        chipLocation = findViewById(R.id.chipLocation)
        toggleButton = findViewById(R.id.toggleButton)
        locationPermissionButton = findViewById(R.id.locationPermissionButton)
        val saveButton: MaterialButton = findViewById(R.id.saveButton)
        val permissionButton: MaterialButton = findViewById(R.id.permissionButton)

        ipInput.setText(Prefs.getTestIp(this))
        hostInput.setText(Prefs.getHostname(this))
        delayInput.setText(Prefs.getProbeDelaySeconds(this).toString())
        retryInput.setText(Prefs.getRetryCount(this).toString())
        domainInput.setText(Prefs.getProbeDomain(this))
        ssidInput.setText(Prefs.getSsidListRaw(this))

        modeRadioGroup.check(modeToRadioId(Prefs.getMode(this)))
        updateModeUi(Prefs.getMode(this))

        modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = radioIdToMode(checkedId)
            Prefs.setMode(this, mode)
            updateModeUi(mode)
            updateStatus()
        }

        saveButton.setOnClickListener {
            saveCurrentValues()
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, R.string.toast_mode4_location, Toast.LENGTH_LONG).show()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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

        modeExplanationText.setText(
            when (mode) {
                Mode.DNS_PROBE -> R.string.explain_dns_probe
                Mode.SYSTEM_RESOLVE -> R.string.explain_system_resolve
                Mode.PING -> R.string.explain_ping
                Mode.WIFI_SSID -> R.string.explain_wifi_ssid
            }
        )
    }

    private fun saveCurrentValues() {
        val delaySeconds = delayInput.text.toString().trim().toIntOrNull() ?: 2
        val retryCount = retryInput.text.toString().trim().toIntOrNull() ?: 3
        val domain = domainInput.text.toString().trim().ifEmpty { "google.com" }
        Prefs.setValues(
            this,
            ipInput.text.toString().trim(),
            hostInput.text.toString().trim(),
            delaySeconds,
            domain,
            ssidInput.text.toString().trim(),
            retryCount
        )
    }

    private fun updateStatus() {
        val running = Prefs.isMonitorEnabled(this)
        val mode = Prefs.getMode(this)

        toggleButton.setText(if (running) R.string.action_stop else R.string.action_start)
        statusTitle.setText(if (running) R.string.status_running else R.string.status_stopped)
        statusSubtitle.text = getString(R.string.status_mode, mode.label, Prefs.getRetryCount(this))

        val containerAttr = if (running) {
            com.google.android.material.R.attr.colorPrimaryContainer
        } else {
            com.google.android.material.R.attr.colorSurfaceContainerHighest
        }
        statusCard.setCardBackgroundColor(MaterialColors.getColor(statusCard, containerAttr))

        val onTitleAttr = if (running) {
            com.google.android.material.R.attr.colorOnPrimaryContainer
        } else {
            com.google.android.material.R.attr.colorOnSurface
        }
        val onSubtitleAttr = if (running) {
            com.google.android.material.R.attr.colorOnPrimaryContainer
        } else {
            com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        statusTitle.setTextColor(MaterialColors.getColor(statusTitle, onTitleAttr))
        statusSubtitle.setTextColor(MaterialColors.getColor(statusSubtitle, onSubtitleAttr))

        bindPermissionChip(
            chipSecureSettings,
            granted = PrivateDnsManager.hasPermission(this),
            okText = R.string.chip_secure_ok,
            missingText = R.string.chip_secure_missing
        )

        chipLocation.visibility = if (mode == Mode.WIFI_SSID) View.VISIBLE else View.GONE
        bindPermissionChip(
            chipLocation,
            granted = hasLocationPermission(),
            okText = R.string.chip_location_ok,
            missingText = R.string.chip_location_missing
        )
    }

    private fun bindPermissionChip(chip: Chip, granted: Boolean, okText: Int, missingText: Int) {
        chip.setText(if (granted) okText else missingText)
        chip.setChipIconResource(if (granted) R.drawable.ic_check_circle else R.drawable.ic_error)
        val tintAttr = if (granted) {
            com.google.android.material.R.attr.colorPrimary
        } else {
            com.google.android.material.R.attr.colorError
        }
        chip.chipIconTint = ColorStateList.valueOf(MaterialColors.getColor(chip, tintAttr))
    }

    private fun showPermissionInstructions() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_permission_title)
            .setMessage(getString(R.string.dialog_permission_message, packageName))
            .setPositiveButton(R.string.dialog_got_it, null)
            .show()
    }

    companion object {
        private const val REQUEST_LOCATION = 2
    }
}
