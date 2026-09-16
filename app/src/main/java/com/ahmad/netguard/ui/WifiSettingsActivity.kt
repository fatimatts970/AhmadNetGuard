package com.ahmad.netguard.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.databinding.ActivityWifiSettingsBinding
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.launch

class WifiSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWifiSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWifiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnToggleWifiPassword.setOnClickListener {
            togglePasswordVisibility(binding.inputWifiPassword, binding.btnToggleWifiPassword)
        }
        binding.btnToggleGuestPassword.setOnClickListener {
            togglePasswordVisibility(binding.inputGuestPassword, binding.btnToggleGuestPassword)
        }

        binding.btnAddGuestWifi.setOnClickListener {
            val ssid = binding.inputGuestSsid.text.toString().trim()
            val pass = binding.inputGuestPassword.text.toString().trim()
            if (ssid.isEmpty() || pass.length < 8) {
                Toast.makeText(this, "Enter a guest name and a password (8+ characters)", Toast.LENGTH_SHORT).show()
            } else {
                lifecycleScope.launch {
                    binding.progressGuestWifi.visibility = android.view.View.VISIBLE
                    val router = RouterAdapterFactory.getAdapter()
                    val success = router.setGuestWifi(ssid, pass, true)
                    binding.progressGuestWifi.visibility = android.view.View.GONE
                    val msg = if (success) "Guest WiFi is on: $ssid" else "Failed! Check router connection."
                    Toast.makeText(this@WifiSettingsActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnRemoveGuestWifi.setOnClickListener {
            lifecycleScope.launch {
                binding.progressGuestWifi.visibility = android.view.View.VISIBLE
                val router = RouterAdapterFactory.getAdapter()
                val ssid = binding.inputGuestSsid.text.toString().trim().ifEmpty { "Guest" }
                val success = router.setGuestWifi(ssid, "00000000", false)
                binding.progressGuestWifi.visibility = android.view.View.GONE
                val msg = if (success) "Guest WiFi turned off" else "Failed! Check router connection."
                Toast.makeText(this@WifiSettingsActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSaveWifiSettings.setOnClickListener {
            val ssid = binding.inputSsid.text.toString().trim()
            val password = binding.inputWifiPassword.text.toString().trim()

            if (ssid.isEmpty() || password.isEmpty()) {
                binding.textWifiError.text = "SSID and Password required"
                binding.textWifiError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            lifecycleScope.launch {
                binding.btnSaveWifiSettings.isEnabled = false
                binding.progressWifiSave.visibility = View.VISIBLE
                binding.textWifiError.visibility = View.GONE

                val router = RouterAdapterFactory.getAdapter()
                val success = router.updateWifiSettings(ssid, password)

                binding.btnSaveWifiSettings.isEnabled = true
                binding.progressWifiSave.visibility = View.GONE

                if (success) {
                    Toast.makeText(this@WifiSettingsActivity, "Wi-Fi Settings Updated!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    binding.textWifiError.text = "Update Failed! Check credentials."
                    binding.textWifiError.visibility = View.VISIBLE
                }
            }
        }

        binding.btnRebootRouter.setOnClickListener {
            lifecycleScope.launch {
                binding.btnRebootRouter.isEnabled = false
                val router = RouterAdapterFactory.getAdapter()
                val success = router.restartRouter()
                binding.btnRebootRouter.isEnabled = true
                val msg = if (success) "Router is rebooting..." else "Reboot failed! Check connection."
                Toast.makeText(this@WifiSettingsActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val passwordVisibilityState = mutableMapOf<Int, Boolean>()

    private fun togglePasswordVisibility(input: android.widget.EditText, icon: android.widget.ImageView) {
        val isCurrentlyVisible = passwordVisibilityState[input.id] ?: false
        if (isCurrentlyVisible) {
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            icon.setImageResource(android.R.drawable.ic_menu_view)
        } else {
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            icon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        }
        passwordVisibilityState[input.id] = !isCurrentlyVisible
        input.setSelection(input.text.length)
    }
}
