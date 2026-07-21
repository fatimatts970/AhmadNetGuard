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
import kotlinx.coroutines.launch

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mac = intent.getStringExtra(EXTRA_MAC) ?: ""
        val name = intent.getStringExtra(EXTRA_NAME) ?: mac
        binding.textHistoryTitle.text = "$name — History"

        val dao = AppDatabase.getInstance(this).connectionEventDao()
        val adapter = HistoryAdapter()
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        lifecycleScope.launch {
            adapter.submitList(dao.getHistoryForDevice(mac))
        }

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear history?")
                .setMessage("This will permanently delete the online/offline log for this device.")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        dao.clearHistoryForDevice(mac)
                        adapter.submitList(emptyList())
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
