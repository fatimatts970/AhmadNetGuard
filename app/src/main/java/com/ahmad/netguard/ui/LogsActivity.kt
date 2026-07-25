package com.ahmad.netguard.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ahmad.netguard.databinding.ActivityLogsBinding
import com.ahmad.netguard.history.AppDatabase
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        binding.btnExportLogs.setOnClickListener {
            exportLogs()
        }
    }

    private fun exportLogs() {
        lifecycleScope.launch {
            val logs = AppDatabase.getInstance(this@LogsActivity).appLogDao().getAllLogs()
            if (logs.isEmpty()) return@launch

            val formatter = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
            val content = buildString {
                appendLine("AHMAD NetGuard — Activity Log Export")
                appendLine("Exported: ${formatter.format(Date())}")
                appendLine("=".repeat(40))
                for (log in logs) {
                    appendLine("[${log.type}] ${formatter.format(Date(log.timestampMillis))}")
                    appendLine("  ${log.message} — ${if (log.success) "SUCCESS" else "FAILED"}")
                }
            }

            val exportDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports")
            exportDir.mkdirs()
            val file = File(exportDir, "netguard_logs_${System.currentTimeMillis()}.txt")
            file.writeText(content)

            val uri = FileProvider.getUriForFile(this@LogsActivity, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Export Logs"))
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
