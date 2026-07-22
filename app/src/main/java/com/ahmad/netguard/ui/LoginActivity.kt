package com.ahmad.netguard.ui

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.databinding.ActivityLoginBinding
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.launch
import java.net.InetAddress

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var credentialStore: RouterCredentialStore
    private val router = RouterAdapterFactory.create(RouterAdapterFactory.Brand.HUAWEI)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credentialStore = RouterCredentialStore(this)

        if (credentialStore.hasSavedCredentials()) {
            binding.inputRouterIp.setText(credentialStore.getRouterIp())
            binding.inputUsername.setText(credentialStore.getUsername())
        } else {
            detectGatewayIp()?.let { binding.inputRouterIp.setText(it) }
        }

        if (BiometricHelper.canUseBiometrics(this) && credentialStore.hasSavedCredentials()) {
            binding.btnUseBiometric.visibility = android.view.View.VISIBLE
            binding.btnUseBiometric.setOnClickListener {
                BiometricHelper.prompt(
                    activity = this,
                    onSuccess = { attemptLogin(useSaved = true) },
                    onFailure = { }
                )
            }
        }

        binding.btnConnect.setOnClickListener {
            attemptLogin(useSaved = false)
        }
    }

    private fun detectGatewayIp(): String? {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val gateway = wifiManager.dhcpInfo?.gateway ?: return null
            if (gateway == 0) return null
            val bytes = byteArrayOf(
                (gateway and 0xFF).toByte(),
                (gateway shr 8 and 0xFF).toByte(),
                (gateway shr 16 and 0xFF).toByte(),
                (gateway shr 24 and 0xFF).toByte()
            )
            InetAddress.getByAddress(bytes).hostAddress
        } catch (e: Exception) {
            null
        }
    }

    private fun attemptLogin(useSaved: Boolean) {
        val ip: String
        val username: String
        val password: String

        if (useSaved) {
            ip = credentialStore.getRouterIp()
            username = credentialStore.getUsername()
            password = credentialStore.getPassword()
        } else {
            ip = binding.inputRouterIp.text.toString().trim().ifBlank { "192.168.100.1" }
            username = binding.inputUsername.text.toString().trim()
            password = binding.inputPassword.text.toString()

            if (username.isBlank() || password.isBlank()) {
                showError("Username and password can't be empty.")
                return
            }
        }

        setLoading(true)
        lifecycleScope.launch {
            val success = router.login(ip, username, password)
            setLoading(false)

            if (success) {
                credentialStore.save(ip, username, password)
                startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                finish()
            } else {
                showError("Could not connect. Check the router IP, username and password, and that your phone is on the router's WiFi.")
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressConnecting.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnConnect.isEnabled = !loading
        binding.btnConnect.text = if (loading) "Connecting…" else "Connect"
    }

    private fun showError(message: String) {
        binding.textLoginError.text = message
        binding.textLoginError.visibility = android.view.View.VISIBLE
    }
}
