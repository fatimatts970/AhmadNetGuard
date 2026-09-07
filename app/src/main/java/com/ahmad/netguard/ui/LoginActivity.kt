package com.ahmad.netguard.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.databinding.ActivityLoginBinding
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var credStore: RouterCredentialStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credStore = RouterCredentialStore(this)

        val savedGateway = credStore.getGateway()
        val savedUsername = credStore.getUsername()
        if (savedGateway.isNotBlank()) binding.inputRouterIp.setText(savedGateway)
        if (savedUsername.isNotBlank()) binding.inputUsername.setText(savedUsername)

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
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
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
