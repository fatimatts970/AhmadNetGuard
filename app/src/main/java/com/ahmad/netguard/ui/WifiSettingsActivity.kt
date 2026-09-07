package com.ahmad.netguard.ui

import android.os.Bundle
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

        binding.btnSaveWifi.setOnClickListener {
            val ssid = binding.etSsid.text.toString().trim()
            val password = binding.etWifiPassword.text.toString().trim()

            if (ssid.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "SSID and Password required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                binding.btnSaveWifi.isEnabled = false
                binding.btnSaveWifi.text = "Saving..."

                val router = RouterAdapterFactory.getAdapter()
                val success = router.updateWifiSettings(ssid, password)

                binding.btnSaveWifi.isEnabled = true
                binding.btnSaveWifi.text = "Save & Reboot"

                if (success) {
                    Toast.makeText(this@WifiSettingsActivity, "Wi-Fi Settings Updated!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@WifiSettingsActivity, "Update Failed! Check credentials.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
