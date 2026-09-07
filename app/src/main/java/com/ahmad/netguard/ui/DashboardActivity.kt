package com.ahmad.netguard.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ahmad.netguard.R
import com.ahmad.netguard.history.ConnectionMonitorService
import com.ahmad.netguard.network.HuaweiRouterAdapter
import com.ahmad.netguard.network.RouterSession
import com.ahmad.netguard.network.RouterCredentialStore
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DashboardActivity : AppCompatActivity() {

    private val routerAdapter = RouterSession.adapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvOnlineBadge: TextView
    private lateinit var tvDownloadSpeed: TextView
    private lateinit var tvUploadSpeed: TextView
    private lateinit var tvUptime: TextView
    private lateinit var tvInternetStatus: TextView
    private lateinit var tvRouterStatus: TextView
    private lateinit var tvWifiStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        swipeRefresh = findViewById(R.id.swipeRefreshDashboard)
        tvOnlineBadge = findViewById(R.id.text_online_badge)
        tvDownloadSpeed = findViewById(R.id.text_download_speed)
        tvUploadSpeed = findViewById(R.id.text_upload_speed)
        tvUptime = findViewById(R.id.text_router_uptime)
        tvInternetStatus = findViewById(R.id.text_internet_status)
        tvRouterStatus = findViewById(R.id.text_router_status)
        tvWifiStatus = findViewById(R.id.text_wifi_status)

        swipeRefresh.setOnRefreshListener { loadDashboardData() }

        findViewById<androidx.cardview.widget.CardView>(R.id.tile_devices).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<androidx.cardview.widget.CardView>(R.id.tile_wifi).setOnClickListener {
            startActivity(Intent(this, WifiSettingsActivity::class.java))
        }

        findViewById<androidx.cardview.widget.CardView>(R.id.tile_net_stats).setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
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

        tvDownloadSpeed.setOnClickListener { runSpeedTest() }
    }

    private fun runSpeedTest() {
        tvDownloadSpeed.text = "Testing…"
        lifecycleScope.launch {
            val mbps = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url("https://speed.cloudflare.com/__down?bytes=10000000")
                        .build()

                    val startTime = System.currentTimeMillis()
                    var bytesRead = 0L
                    client.newCall(request).execute().use { response ->
                        val body = response.body ?: return@withContext null
                        val source = body.source()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) break
                            bytesRead += read
                        }
                    }
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                    if (elapsedSeconds <= 0) return@withContext null
                    (bytesRead * 8) / (elapsedSeconds * 1_000_000)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            tvDownloadSpeed.text = if (mbps != null) {
                "%.1f Mbps".format(mbps)
            } else {
                "Test failed"
            }
        }
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

                tvRouterStatus.text = "Online"
                tvWifiStatus.text = if (devices.isNotEmpty()) "Active" else "Unknown"
                tvUptime.text = "Not available yet"
                tvDownloadSpeed.text = "Tap to test"
                tvUploadSpeed.text = "Not available yet"

                val wanInfo = (routerAdapter as? HuaweiRouterAdapter)?.getWanInfo()
                tvInternetStatus.text = if (wanInfo != null && wanInfo.wanIp != "Unknown") {
                    "Connected (${wanInfo.wanIp})"
                } else {
                    "Not available"
                }
            } catch (e: Exception) {
                Snackbar.make(swipeRefresh, "Lost connection to router", Snackbar.LENGTH_LONG)
                    .setAction("Login Again") {
                        RouterCredentialStore(this@DashboardActivity).clear()
                        startActivity(Intent(this@DashboardActivity, LoginActivity::class.java))
                        finish()
                    }
                    .show()
                tvRouterStatus.text = "Offline"
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }
}
