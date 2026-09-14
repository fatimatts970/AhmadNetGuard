package com.ahmad.netguard.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ahmad.netguard.R
import com.ahmad.netguard.history.ConnectionMonitorService
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DashboardActivity : AppCompatActivity() {

    private val routerAdapter = RouterAdapterFactory.getAdapter()

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvOnlineBadge: TextView
    private lateinit var tvDownloadSpeed: TextView
    private lateinit var tvUploadSpeed: TextView
    private lateinit var tvModemOnlinePill: TextView
    private lateinit var tvWanType: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        swipeRefresh = findViewById(R.id.swipeRefreshDashboard)
        tvOnlineBadge = findViewById(R.id.text_online_badge)
        tvDownloadSpeed = findViewById(R.id.text_download_speed)
        tvUploadSpeed = findViewById(R.id.text_upload_speed)
        tvModemOnlinePill = findViewById(R.id.text_modem_online_pill)
        tvWanType = findViewById(R.id.text_wan_type)

        findViewById<TextView>(R.id.text_run_speed_test).setOnClickListener { runSpeedTest() }
        findViewById<android.widget.Switch>(R.id.switch_guest_wifi).setOnCheckedChangeListener { _, _ ->
            Toast.makeText(
                this,
                "Coming soon — router ka is feature ka API abhi capture nahi hua",
                Toast.LENGTH_SHORT
            ).show()
        }

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
            RouterCredentialStore(this).clearCredentials()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        setupMoreSection()
        startHistoryTracking()
        loadDashboardData()

        tvDownloadSpeed.setOnClickListener { runSpeedTest() }
    }

    private fun setupMoreSection() {
        findViewById<LinearLayout>(R.id.more_extra_ssids).setOnClickListener {
            startActivity(Intent(this, WifiSettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.more_known_devices).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.more_speed_test).setOnClickListener {
            runSpeedTest()
        }

        findViewById<LinearLayout>(R.id.more_reboot).setOnClickListener {
            confirmReboot()
        }

        val comingSoonIds = listOf(
            R.id.more_dhcp,
            R.id.more_ip_filter,
            R.id.more_parental,
            R.id.more_wan_config,
            R.id.more_wan,
            R.id.more_internet_control,
            R.id.more_admin
        )
        for (id in comingSoonIds) {
            findViewById<LinearLayout>(id).setOnClickListener {
                Toast.makeText(
                    this,
                    "Coming soon — router ka is feature ka API abhi capture nahi hua",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun confirmReboot() {
        AlertDialog.Builder(this)
            .setTitle("Reboot Router?")
            .setMessage("Router restart hone tak sab devices ka internet band ho jayega.")
            .setPositiveButton("Reboot") { _, _ ->
                lifecycleScope.launch {
                    val success = routerAdapter.restartRouter()
                    val msg = if (success) "Router is rebooting…" else "Reboot failed! Check connection."
                    Toast.makeText(this@DashboardActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

                tvModemOnlinePill.text = "● ONLINE"
                tvModemOnlinePill.setTextColor(getColor(R.color.green_online))
                tvWanType.text = "🌐 Connected"
                tvDownloadSpeed.text = "Tap to test"
                tvUploadSpeed.text = "Not available yet"
            } catch (e: Exception) {
                Snackbar.make(swipeRefresh, "Lost connection to router", Snackbar.LENGTH_LONG)
                    .setAction("Login Again") {
                        RouterCredentialStore(this@DashboardActivity).clearCredentials()
                        startActivity(Intent(this@DashboardActivity, LoginActivity::class.java))
                        finish()
                    }
                    .show()
                tvModemOnlinePill.text = "● OFFLINE"
                tvModemOnlinePill.setTextColor(getColor(R.color.danger))
                tvWanType.text = "🌐 Not available"
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }
}
