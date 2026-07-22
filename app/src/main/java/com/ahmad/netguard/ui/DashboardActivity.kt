package com.ahmad.netguard.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.databinding.ActivityDashboardBinding
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var credentialStore: RouterCredentialStore
    private val router = RouterAdapterFactory.create(RouterAdapterFactory.Brand.HUAWEI)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credentialStore = RouterCredentialStore(this)

        val ip = credentialStore.getRouterIp()
        binding.textRouterIp.text = ip
        binding.textWifiIp.text = ip

        binding.tileDevices.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.tileWifi.setOnClickListener {
            Toast.makeText(this, "WiFi settings screen — coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.tileNetStats.setOnClickListener {
            Toast.makeText(this, "Net Stats screen — coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.tileMacFilter.setOnClickListener {
            Toast.makeText(this, "MAC Filter screen — coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.btnLogout.setOnClickListener {
            credentialStore.clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshOnlineCount()
    }

    private fun refreshOnlineCount() {
        lifecycleScope.launch {
            val devices = router.getDevices()
            val onlineCount = devices.count { it.isOnline }
            binding.textOnlineBadge.text = "$onlineCount online"
        }
    }
}
