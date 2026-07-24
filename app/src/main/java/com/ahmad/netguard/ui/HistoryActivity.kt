package com.ahmad.netguard.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmad.netguard.databinding.ActivityHistoryBinding
import com.ahmad.netguard.history.AppDatabase
import com.ahmad.netguard.history.ConnectionEvent
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_MAC = "extra_mac"
        private const val EXTRA_NAME = "extra_name"

        fun start(context: Context, mac: String, displayName: String) {
            val intent = Intent(context, HistoryActivity::class.java)
            intent.putExtra(EXTRA_MAC, mac)
            intent.putExtra(EXTRA_NAME, displayName)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var mac: String
    private var allEvents: List<ConnectionEvent> = emptyList()
    private val adapter = HistoryAdapter()
    private val summaryFormatter = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mac = intent.getStringExtra(EXTRA_MAC) ?: ""
        val name = intent.getStringExtra(EXTRA_NAME) ?: mac
        binding.textHistoryTitle.text = "$name — History"

        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        loadHistory()

        binding.tabHistoryRange.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) = applyRangeFilter(tab?.position ?: 0)
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear history?")
                .setMessage("This will permanently delete the online/offline log for this device.")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        val dao = AppDatabase.getInstance(this@HistoryActivity).connectionEventDao()
                        dao.clearHistoryForDevice(mac)
                        loadHistory()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@HistoryActivity).connectionEventDao()
            allEvents = dao.getHistoryForDevice(mac)
            updateSummary()
            applyRangeFilter(binding.tabHistoryRange.selectedTabPosition.coerceAtLeast(0))
        }
    }

    private fun applyRangeFilter(tabPosition: Int) {
        val cutoff = when (tabPosition) {
            0 -> startOfToday()
            1 -> startOfToday() - 6L * 24 * 60 * 60 * 1000
            2 -> startOfToday() - 29L * 24 * 60 * 60 * 1000
            else -> 0L
        }
        val filtered = allEvents.filter { it.timestampMillis >= cutoff }
        adapter.submitList(filtered)
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun updateSummary() {
        if (allEvents.isEmpty()) {
            binding.textFirstSeen.text = "—"
            binding.textLastSeen.text = "—"
            binding.textTotalOnlineDuration.text = "—"
            binding.textEventsToday.text = "0"
            return
        }

        val oldest = allEvents.last()
        val newest = allEvents.first()
        binding.textFirstSeen.text = summaryFormatter.format(Date(oldest.timestampMillis))
        binding.textLastSeen.text = summaryFormatter.format(Date(newest.timestampMillis))

        val chronological = allEvents.sortedBy { it.timestampMillis }
        var totalOnlineMs = 0L
        var connectStart: Long? = null
        for (event in chronological) {
            if (event.eventType == "connected") {
                connectStart = event.timestampMillis
            } else if (event.eventType == "disconnected" && connectStart != null) {
                totalOnlineMs += event.timestampMillis - connectStart
                connectStart = null
            }
        }
        if (connectStart != null) {
            totalOnlineMs += System.currentTimeMillis() - connectStart
        }
        binding.textTotalOnlineDuration.text = formatDuration(totalOnlineMs)

        val todayStart = startOfToday()
        binding.textEventsToday.text = allEvents.count { it.timestampMillis >= todayStart }.toString()
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = ms / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}
