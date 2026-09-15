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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class DashboardActivity : AppCompatActivity() {

    private val routerAdapter = RouterAdapterFactory.getAdapter()

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvOnlineBadge: TextView
    private lateinit var tvDownloadSpeed: TextView
    private lateinit var tvUploadSpeed: TextView
    private lateinit var tvModemOnlinePill: TextView
    private lateinit var tvWanType: TextView
    private lateinit var tvCpuPercent: TextView
    private lateinit var tvWifiName: TextView
    private lateinit var tvModemName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        swipeRefresh = findViewById(R.id.swipeRefreshDashboard)
        tvOnlineBadge = findViewById(R.id.text_online_badge)
        tvDownloadSpeed = findViewById(R.id.text_download_speed)
        tvUploadSpeed = findViewById(R.id.text_upload_speed)
        tvModemOnlinePill = findViewById(R.id.text_modem_online_pill)
        tvWanType = findViewById(R.id.text_wan_type)
        tvCpuPercent = findViewById(R.id.text_cpu_percent)
        tvWifiName = findViewById(R.id.text_wifi_name)
        tvModemName = findViewById(R.id.text_modem_name)

        // Never show the placeholder sample names even for a frame — replace them
        // with a loading state immediately, real values arrive from loadDashboardData().
        tvWifiName.text = "Loading…"
        tvModemName.text = "Loading…"

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
            startActivity(Intent(this, MacFilterActivity::class.java))
        }

        findViewById<android.widget.ImageButton>(R.id.btn_logout).setOnClickListener {
            RouterCredentialStore(this).clearCredentials()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        setupMoreSection()
        startHistoryTracking()
        loadDashboardData()
        runSpeedTest()

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
        tvUploadSpeed.text = "Waiting…"
        lifecycleScope.launch {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build()

            // Fast.com (Netflix) speed test — same public token fast.com's own
            // webpage uses, verified via real packet capture.
            val targetUrl = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://api.fast.com/netflix/speedtest/v2?https=true&token=YXNkZmFzZGxmbnNkYWZoYXNkZmhrYWxm&urlCount=1")
                        .build()
                    val body = client.newCall(request).execute().use { it.body?.string() }
                    body?.let { Regex("\"url\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (targetUrl == null) {
                tvDownloadSpeed.text = "Test failed"
                tvUploadSpeed.text = "Test failed"
                return@launch
            }

            val downloadMbps = withContext(Dispatchers.IO) {
                try {
                    // 100MB cap is just a ceiling — we stop reading after ~5s regardless,
                    // so this never depends on waiting for the full body to arrive.
                    val rangeUrl = targetUrl.replace("/speedtest?", "/speedtest/range/0-104857600?")
                    val request = Request.Builder().url(rangeUrl).post(ByteArray(0).toRequestBody(null)).build()

                    val durationMillis = 5000L
                    var bytesRead = 0L
                    val startTime = System.currentTimeMillis()
                    client.newCall(request).execute().use { response ->
                        val source = response.body?.source() ?: return@withContext null
                        val buffer = ByteArray(65536)
                        while (System.currentTimeMillis() - startTime < durationMillis) {
                            val read = source.read(buffer)
                            if (read == -1) break
                            bytesRead += read
                        }
                    }
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                    if (elapsedSeconds <= 0 || bytesRead <= 0) null else (bytesRead * 8) / (elapsedSeconds * 1_000_000)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            tvDownloadSpeed.text = if (downloadMbps != null) "%.1f Mbps".format(downloadMbps) else "Test failed"

            tvUploadSpeed.text = "Testing…"
            val uploadMbps = withContext(Dispatchers.IO) {
                try {
                    val rangeUrl = targetUrl.replace("/speedtest?", "/speedtest/range/0-2048?")
                    val chunk = ByteArray(2048)
                    val durationMillis = 4000L
                    val startTime = System.currentTimeMillis()
                    var totalBytesSent = 0L

                    while (System.currentTimeMillis() - startTime < durationMillis) {
                        val request = Request.Builder()
                            .url(rangeUrl)
                            .post(chunk.toRequestBody("application/octet-stream".toMediaType()))
                            .build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) totalBytesSent += chunk.size
                        }
                    }
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                    if (elapsedSeconds <= 0 || totalBytesSent <= 0) null else (totalBytesSent * 8) / (elapsedSeconds * 1_000_000)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            tvUploadSpeed.text = if (uploadMbps != null) {
                "%.1f Mbps".format(uploadMbps)
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

                val cpu = routerAdapter.getCpuUsagePercent()
                tvCpuPercent.text = if (cpu != null) "$cpu%" else "—"

                val ssid = routerAdapter.getWifiSsidName()
                if (!ssid.isNullOrBlank()) tvWifiName.text = ssid

                val model = routerAdapter.getRouterModel()
                tvModemName.text = if (!model.isNullOrBlank()) "Huawei $model" else "Huawei Router"
            } catch (e: Exception) {
                if (tvWifiName.text == "Loading…") tvWifiName.text = "Unavailable"
                if (tvModemName.text == "Loading…") tvModemName.text = "Huawei Router"
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
