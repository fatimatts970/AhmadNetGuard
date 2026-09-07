package com.ahmad.netguard.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmad.netguard.databinding.ActivityDashboardBinding
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val adapter = DeviceListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = adapter

        // Button Actions
        binding.btnWifiSettings.setOnClickListener {
            startActivity(Intent(this, WifiSettingsActivity::class.java))
        }
        binding.btnLogs.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // Pehli baar load
        loadDevices()
    }

    // Jab bhi user Dashboard par wapas aaye (WiFi settings ya block ke baad), refresh ho jaye
    override fun onResume() {
        super.onResume()
        loadDevices() // Real-time update ke liye
    }

    private fun loadDevices() {
        lifecycleScope.launch {
            try {
                val router = RouterAdapterFactory.getAdapter()
                val devices = router.getConnectedDevices()
                if (devices.isNotEmpty()) {
                    adapter.submitList(devices)
                } else {
                    Toast.makeText(this@DashboardActivity, "No devices found or check login", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
