package com.ahmad.netguard.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.R
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.launch

class WanConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wan_config)

        findViewById<ImageButton>(R.id.btnBackWanConfig).setOnClickListener { finish() }

        val statusPill = findViewById<TextView>(R.id.text_wan_status_pill)
        lifecycleScope.launch {
            try {
                val router = RouterAdapterFactory.getAdapter()
                router.getDevices()
                statusPill.text = "● Connected"
                statusPill.setTextColor(getColor(R.color.green_online))
            } catch (e: Exception) {
                statusPill.text = "● Offline"
                statusPill.setTextColor(getColor(R.color.danger))
            }
        }
    }
}
