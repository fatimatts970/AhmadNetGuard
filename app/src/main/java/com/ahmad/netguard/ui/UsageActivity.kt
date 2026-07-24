package com.ahmad.netguard.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.databinding.ActivityUsageBinding
import com.ahmad.netguard.history.AppDatabase
import kotlinx.coroutines.launch

class UsageActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_MAC = "extra_mac"
        private const val EXTRA_NAME = "extra_name"

        fun start(context: Context, mac: String, displayName: String) {
            val intent = Intent(context, UsageActivity::class.java)
            intent.putExtra(EXTRA_MAC, mac)
            intent.putExtra(EXTRA_NAME, displayName)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityUsageBinding
    private lateinit var mac: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mac = intent.getStringExtra(EXTRA_MAC) ?: ""
        val name = intent.getStringExtra(EXTRA_NAME) ?: mac
        binding.textUsageTitle.text = "$name — Usage"

        binding.btnClearUsage.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset usage data?")
                .setMessage("This clears the estimated usage totals for this device. It can't be undone.")
                .setPositiveButton("Reset") { _, _ ->
                    lifecycleScope.launch {
                        AppDatabase.getInstance(this@UsageActivity).usageDao().clearForDevice(mac)
                        loadUsage()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        loadUsage()
    }

    private fun loadUsage() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@UsageActivity).usageDao()
            val todayEpoch = System.currentTimeMillis() / 86_400_000L

            val today = dao.getBytesForDay(mac, todayEpoch) ?: 0L
            val yesterday = dao.getBytesForDay(mac, todayEpoch - 1) ?: 0L
            val week = dao.getBytesInRange(mac, todayEpoch - 6, todayEpoch)
            val month = dao.getBytesInRange(mac, todayEpoch - 29, todayEpoch)
            val total = dao.getTotalBytes(mac)

            binding.textUsageToday.text = formatBytes(today)
            binding.textUsageYesterday.text = formatBytes(yesterday)
            binding.textUsageWeek.text = formatBytes(week)
            binding.textUsageMonth.text = formatBytes(month)
            binding.textUsageTotal.text = formatBytes(total)
        }
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
