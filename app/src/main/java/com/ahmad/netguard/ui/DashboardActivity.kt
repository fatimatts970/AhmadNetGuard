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

        // Buttons
        binding.btnWifiSettings.setOnClickListener {
            startActivity(Intent(this, WifiSettingsActivity::class.java))
        }

        binding.btnLogs.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // Real devices load karo
        loadDevices()
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
                Toast.makeText(this@DashboardActivity, "Error loading devices: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
