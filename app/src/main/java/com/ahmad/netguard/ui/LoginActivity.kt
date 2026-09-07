package com.ahmad.netguard.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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

        binding.btnLogin.setOnClickListener {
            val gateway = binding.etGateway.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (gateway.isNotEmpty() && pass.isNotEmpty()) {
                // Perform actual login
                lifecycleScope.launch {
                    binding.btnLogin.isEnabled = false
                    binding.btnLogin.text = "Logging in..."

                    val adapter = RouterAdapterFactory.getAdapter()
                    val success = adapter.login(gateway, "admin", pass)

                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "Connect to Router"

                    if (success) {
                        credStore.saveCredentials(gateway, "admin", pass)
                        Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "Login Failed! Check IP/Password.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Enter IP and Password", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
