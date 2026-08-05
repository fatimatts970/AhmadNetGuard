package com.ahmad.netguard.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.R
import com.ahmad.netguard.network.HuaweiRouterAdapter
import com.ahmad.netguard.network.RouterCredentialStore
import com.ahmad.netguard.network.RouterSession
import kotlinx.coroutines.launch

class WifiSettingsActivity : AppCompatActivity() {

    private val routerAdapter = RouterSession.adapter
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_settings)

        val inputSsid = findViewById<android.widget.EditText>(R.id.inputSsid)
        val inputPassword = findViewById<android.widget.EditText>(R.id.inputWifiPassword)
        val btnToggle = findViewById<android.widget.ImageView>(R.id.btnToggleWifiPassword)
        val btnSave = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveWifiSettings)
        val progress = findViewById<android.widget.ProgressBar>(R.id.progressWifiSave)
        val textError = findViewById<android.widget.TextView>(R.id.textWifiError)

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            inputPassword.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            inputPassword.setSelection(inputPassword.text.length)
        }

        btnSave.setOnClickListener {
            val ssid = inputSsid.text.toString().trim()
            val password = inputPassword.text.toString()

            if (ssid.isBlank() || password.length < 8) {
                textError.text = "Network name can't be empty and password needs at least 8 characters."
                textError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Change WiFi settings?")
                .setMessage("This disconnects every device on your network, including this phone. You'll need to reconnect to \"$ssid\" and log back in.")
                .setPositiveButton("Change") { _, _ -> saveWifiSettings(ssid, password, btnSave, progress, textError) }
                .setNegativeButton("Cancel", null)
                .show()
        }

        lifecycleScope.launch {
            val currentSsid = (routerAdapter as? HuaweiRouterAdapter)?.getCurrentWifiName()
            if (!currentSsid.isNullOrBlank()) {
                inputSsid.setText(currentSsid)
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRebootRouter).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reboot router?")
                .setMessage("This restarts the router. Every device on the network (including this one) will lose connection for a minute or two while it comes back up.")
                .setPositiveButton("Reboot") { _, _ -> triggerReboot() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        setupGuestWifi()
    }

    private fun setupGuestWifi() {
        val inputGuestSsid = findViewById<android.widget.EditText>(R.id.inputGuestSsid)
        val inputGuestPassword = findViewById<android.widget.EditText>(R.id.inputGuestPassword)
        val textGuestError = findViewById<android.widget.TextView>(R.id.textGuestError)
        val btnAddGuest = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddGuestWifi)
        val btnRemoveGuest = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRemoveGuestWifi)
        val progressGuest = findViewById<android.widget.ProgressBar>(R.id.progressGuestWifi)

        var isGuestPasswordVisible = false
        findViewById<android.widget.ImageView>(R.id.btnToggleGuestPassword).setOnClickListener {
            isGuestPasswordVisible = !isGuestPasswordVisible
            inputGuestPassword.inputType = if (isGuestPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            inputGuestPassword.setSelection(inputGuestPassword.text.length)
        }

        btnAddGuest.setOnClickListener {
            val guestSsid = inputGuestSsid.text.toString().trim()
            val guestPassword = inputGuestPassword.text.toString()

            if (guestSsid.isBlank() || guestPassword.length < 8) {
                textGuestError.text = "Guest network name can't be empty and password needs at least 8 characters."
                textGuestError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            textGuestError.visibility = View.GONE
            btnAddGuest.isEnabled = false
            btnRemoveGuest.isEnabled = false
            progressGuest.visibility = View.VISIBLE

            lifecycleScope.launch {
                val success = (routerAdapter as? HuaweiRouterAdapter)?.addGuestSsid(guestSsid, guestPassword) ?: false
                progressGuest.visibility = View.GONE
                btnAddGuest.isEnabled = true
                btnRemoveGuest.isEnabled = true

                if (success) {
                    AlertDialog.Builder(this@WifiSettingsActivity)
                        .setTitle("Guest WiFi turned on")
                        .setMessage("Guests can now connect to \"$guestSsid\".")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    textGuestError.text = "Couldn't turn on Guest WiFi. Check your connection and try again."
                    textGuestError.visibility = View.VISIBLE
                }
            }
        }

        btnRemoveGuest.setOnClickListener {
            val guestSsid = inputGuestSsid.text.toString().trim()
            if (guestSsid.isBlank()) {
                textGuestError.text = "Enter the guest network name to turn it off."
                textGuestError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            textGuestError.visibility = View.GONE
            btnAddGuest.isEnabled = false
            btnRemoveGuest.isEnabled = false
            progressGuest.visibility = View.VISIBLE

            lifecycleScope.launch {
                val success = (routerAdapter as? HuaweiRouterAdapter)?.deleteGuestSsid(guestSsid) ?: false
                progressGuest.visibility = View.GONE
                btnAddGuest.isEnabled = true
                btnRemoveGuest.isEnabled = true

                if (success) {
                    AlertDialog.Builder(this@WifiSettingsActivity)
                        .setTitle("Guest WiFi turned off")
                        .setMessage("\"$guestSsid\" has been removed.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    textGuestError.text = "Couldn't turn off Guest WiFi. Check your connection and try again."
                    textGuestError.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun triggerReboot() {
        lifecycleScope.launch {
            val success = (routerAdapter as? HuaweiRouterAdapter)?.rebootRouter() ?: false
            AlertDialog.Builder(this@WifiSettingsActivity)
                .setTitle(if (success) "Rebooting…" else "Couldn't reboot")
                .setMessage(
                    if (success) "The router is restarting. Give it a minute or two, then reconnect and log in again."
                    else "The reboot request failed. Check your connection and try again."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun saveWifiSettings(
        ssid: String,
        password: String,
        btnSave: com.google.android.material.button.MaterialButton,
        progress: android.widget.ProgressBar,
        textError: android.widget.TextView
    ) {
        textError.visibility = View.GONE
        btnSave.isEnabled = false
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val success = (routerAdapter as? HuaweiRouterAdapter)?.changeWifiSettings(ssid, password) ?: false
            progress.visibility = View.GONE
            btnSave.isEnabled = true

            if (success) {
                AlertDialog.Builder(this@WifiSettingsActivity)
                    .setTitle("WiFi settings updated")
                    .setMessage("Your network name/password changed. This app will now sign you out — reconnect to \"$ssid\" on this phone and log in again.")
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ ->
                        RouterCredentialStore(this@WifiSettingsActivity).clear()
                        val intent = android.content.Intent(this@WifiSettingsActivity, LoginActivity::class.java)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                    .show()
            } else {
                textError.text = "Couldn't change WiFi settings. Check your connection and try again."
                textError.visibility = View.VISIBLE
            }
        }
    }
}
