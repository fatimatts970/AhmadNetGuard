package com.ahmad.netguard.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.R
import com.ahmad.netguard.network.HuaweiRouterAdapter
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private val routerAdapter = HuaweiRouterAdapter()
    private var tvOnlineCount: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        tvOnlineCount = findViewById(R.id.tvOnlineCount)

        findViewById<TextView>(R.id.btnDevices)?.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        loadDashboardData()
    }

    private fun loadDashboardData() {
        lifecycleScope.launch {
            try {
                val devices = routerAdapter.getConnectedDevices()
                val onlineDevices = devices.filter { it.isOnline }
                tvOnlineCount?.text = "${onlineDevices.size} online"
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "Error loading dashboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
