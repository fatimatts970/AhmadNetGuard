package com.ahmad.netguard.ui

import android.app.AlertDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmad.netguard.databinding.ActivityLogsBinding
import com.ahmad.netguard.history.AppDatabase
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private val adapter = LogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerLogs.layoutManager = LinearLayoutManager(this)
        binding.recyclerLogs.adapter = adapter

        loadLogs(null)

        binding.tabLogType.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val type = when (tab?.position) {
                    1 -> "CONNECTION"
                    2 -> "LOGIN"
                    3 -> "BLOCK"
                    4 -> "UNBLOCK"
                    else -> null
                }
                loadLogs(type)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.btnClearLogs.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear all logs?")
                .setMessage("This permanently deletes the entire activity log.")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        AppDatabase.getInstance(this@LogsActivity).appLogDao().clearAllLogs()
                        loadLogs(null)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadLogs(type: String?) {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@LogsActivity).appLogDao()
            val logs = if (type == null) dao.getAllLogs() else dao.getLogsByType(type)
            adapter.submitList(logs)
            binding.textLogsEmpty.visibility = if (logs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.recyclerLogs.visibility = if (logs.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }
    }
}
