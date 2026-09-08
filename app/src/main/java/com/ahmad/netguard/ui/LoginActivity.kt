package com.ahmad.netguard.ui

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.databinding.ActivityLoginBinding
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.launch
import java.net.InetAddress

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var credStore: RouterCredentialStore
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credStore = RouterCredentialStore(this)

        val savedGateway = credStore.getGateway()
        val savedUsername = credStore.getUsername()

        // Agar pehle se saved gateway nahi hai, to phone ke actual WiFi
        // router ka gateway IP auto-detect karke bhar dete hain
        if (savedGateway.isBlank() || savedGateway == "192.168.100.1") {
            val detectedGateway = detectWifiGatewayIp()
            if (detectedGateway != null) {
                binding.inputRouterIp.setText(detectedGateway)
            } else if (savedGateway.isNotBlank()) {
                binding.inputRouterIp.setText(savedGateway)
            }
        } else {
            binding.inputRouterIp.setText(savedGateway)
        }

        if (savedUsername.isNotBlank()) binding.inputUsername.setText(savedUsername)

        // Eye icon — password show/hide toggle
        binding.btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.inputPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnTogglePassword.setImageResource(android.R.drawable.ic_menu_view)
            } else {
                binding.inputPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnTogglePassword.setImageResource(android.R.drawable.ic_secure)
            }
            // Cursor ko end mein rakhna
            binding.inputPassword.setSelection(binding.inputPassword.text?.length ?: 0)
        }

        if (BiometricHelper.canUseBiometrics(this) && credStore.getPassword().isNotBlank()) {
            binding.btnUseBiometric.visibility = View.VISIBLE
            binding.btnUseBiometric.setOnClickListener {
                BiometricHelper.prompt(
                    activity = this,
                    onSuccess = {
                        attemptLogin(credStore.getGateway(), credStore.getUsername(), credStore.getPassword())
                    },
                    onFailure = { showError("Biometric authentication failed") }
                )
            }
        }

        binding.btnConnect.setOnClickListener {
            val gateway = binding.inputRouterIp.text.toString().trim()
            val username = binding.inputUsername.text.toString().trim()
            val pass = binding.inputPassword.text.toString().trim()

            if (gateway.isEmpty() || pass.isEmpty()) {
                showError("Enter IP and Password")
                return@setOnClickListener
            }
            attemptLogin(gateway, username.ifEmpty { "admin" }, pass)
        }
    }

    private fun detectWifiGatewayIp(): String? {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val gatewayInt = wifiManager?.dhcpInfo?.gateway ?: return null
            if (gatewayInt == 0) return null

            val bytes = byteArrayOf(
                (gatewayInt and 0xFF).toByte(),
                (gatewayInt shr 8 and 0xFF).toByte(),
                (gatewayInt shr 16 and 0xFF).toByte(),
                (gatewayInt shr 24 and 0xFF).toByte()
            )
            InetAddress.getByAddress(bytes).hostAddress
        } catch (e: Exception) {
            null
        }
    }

    private fun attemptLogin(gateway: String, username: String, pass: String) {
        lifecycleScope.launch {
            binding.btnConnect.isEnabled = false
            binding.progressConnecting.visibility = View.VISIBLE
            binding.textLoginError.visibility = View.GONE

            val adapter = RouterAdapterFactory.getAdapter()
            val success = adapter.login(gateway, username, pass)

            binding.btnConnect.isEnabled = true
            binding.progressConnecting.visibility = View.GONE

            if (success) {
                credStore.saveCredentials(gateway, username, pass)
                startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                finish()
            } else {
                showError("Could not connect: check IP, username and password")
            }
        }
    }

    private fun showError(message: String) {
        binding.textLoginError.text = message
        binding.textLoginError.visibility = View.VISIBLE
    }
}
