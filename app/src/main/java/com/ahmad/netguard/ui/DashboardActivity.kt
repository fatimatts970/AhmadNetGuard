package com.ahmad.netguard.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ahmad.netguard.R
import com.ahmad.netguard.history.ConnectionMonitorService
import com.ahmad.netguard.network.RouterSession
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private val routerAdapter = RouterSession.adapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvOnlineBadge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        swipeRefresh = findViewById(R.id.swipeRefreshDashboard)
        tvOnlineBadge = findViewById(R.id.text_online_badge)

        swipeRefresh.setOnRefreshListener { loadDashboardData() }

        findViewById<androidx.cardview.widget.CardView>(R.id.tile_devices).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<androidx.cardview.widget.CardView>(R.id.tile_wifi).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<androidx.cardview.widget.CardView>(R.id.tile_net_stats).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<androidx.cardview.widget.CardView>(R.id.tile_mac_filter).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<android.widget.ImageButton>(R.id.btn_logout).setOnClickListener {
            RouterCredentialStore(this).clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        startHistoryTracking()
        loadDashboardData()
    }

    private fun startHistoryTracking() {
        val serviceIntent = Intent(this, ConnectionMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun loadDashboardData() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val devices = routerAdapter.getDevices()
                val onlineDevices = devices.filter { it.isOnline }
                tvOnlineBadge.text = "${onlineDevices.size} online"
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "Error loading dashboard", Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }
}
