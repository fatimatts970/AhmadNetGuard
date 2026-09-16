package com.ahmad.netguard.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.R
import com.ahmad.netguard.history.AppDatabase
import com.ahmad.netguard.network.DeviceNameStore
import kotlinx.coroutines.launch

class NetStatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_net_stats)

        findViewById<android.widget.ImageButton>(R.id.btnBackNetStats).setOnClickListener { finish() }

        loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@NetStatsActivity).usageDao()
            val nameStore = DeviceNameStore(this@NetStatsActivity)
            val todayEpoch = System.currentTimeMillis() / 86_400_000L

            val today = dao.getTotalBytesForDayAllDevices(todayEpoch)
            val yesterday = dao.getTotalBytesForDayAllDevices(todayEpoch - 1)
            val week = dao.getTotalBytesInRangeAllDevices(todayEpoch - 6, todayEpoch)
            val month = dao.getTotalBytesInRangeAllDevices(todayEpoch - 29, todayEpoch)
            val total = dao.getTotalBytesAllDevices()

            findViewById<TextView>(R.id.text_net_stats_today).text = formatBytes(today)
            findViewById<TextView>(R.id.text_net_stats_yesterday).text = formatBytes(yesterday)
            findViewById<TextView>(R.id.text_net_stats_week).text = formatBytes(week)
            findViewById<TextView>(R.id.text_net_stats_month).text = formatBytes(month)
            findViewById<TextView>(R.id.text_net_stats_total).text = formatBytes(total)

            val perDevice = dao.getPerDeviceTotals()
            val container = findViewById<LinearLayout>(R.id.layoutPerDeviceUsage)
            container.removeAllViews()
            perDevice.forEach { entry ->
                val name = nameStore.getCustomName(entry.mac) ?: entry.mac
                container.addView(buildDeviceRow(name, entry.mac, entry.total))
            }
        }
    }

    private fun buildDeviceRow(name: String, mac: String, totalBytes: Long): androidx.cardview.widget.CardView {
        val card = androidx.cardview.widget.CardView(this).apply {
            radius = 32f
            setCardBackgroundColor(getColor(R.color.card_white))
            cardElevation = 0f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = (10 * resources.displayMetrics.density).toInt()
            layoutParams = params
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val pad = (14 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = name
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            text = mac.lowercase()
            setTextColor(getColor(R.color.text_secondary))
            textSize = 11f
        })
        val totalView = TextView(this).apply {
            text = formatBytes(totalBytes)
            setTextColor(getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textSize = 13f
        }
        row.addView(textCol)
        row.addView(totalView)
        card.addView(row)
        return card
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            "%.2f GB".format(mb / 1024.0)
        } else {
            "%.1f MB".format(mb)
        }
    }
}
