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
}
