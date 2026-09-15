package com.ahmad.netguard.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.R
import com.ahmad.netguard.network.DeviceNameStore
import com.ahmad.netguard.network.RouterAdapterFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MacFilterActivity : AppCompatActivity() {

    private val routerAdapter = RouterAdapterFactory.getAdapter()
    private lateinit var nameStore: DeviceNameStore
    private lateinit var layoutRules: LinearLayout
    private lateinit var textNoRules: TextView
    private lateinit var textStatusPill: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mac_filter)

        nameStore = DeviceNameStore(this)
        layoutRules = findViewById(R.id.layoutFilterRules)
        textNoRules = findViewById(R.id.text_no_rules)
        textStatusPill = findViewById(R.id.text_filter_status_pill)

        findViewById<android.widget.ImageButton>(R.id.btnBackMacFilter).setOnClickListener { finish() }
        findViewById<CardView>(R.id.btnAddRule).setOnClickListener { showAddRuleDialog() }

        loadRules()
    }

    private fun loadRules() {
        lifecycleScope.launch {
            try {
                val enabled = routerAdapter.isFilterEnabled()
                textStatusPill.text = if (enabled) "● ON" else "● OFF"
                textStatusPill.setTextColor(getColor(if (enabled) R.color.green_online else R.color.danger))

                val blockedMacs = routerAdapter.getBlockedMacs()
                layoutRules.removeAllViews()

                if (blockedMacs.isEmpty()) {
                    textNoRules.visibility = android.view.View.VISIBLE
                } else {
                    textNoRules.visibility = android.view.View.GONE
                    blockedMacs.sorted().forEach { mac -> layoutRules.addView(buildRuleRow(mac)) }
                }
            } catch (e: Exception) {
                Snackbar.make(layoutRules, "Could not load filter rules from router", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun buildRuleRow(mac: String): CardView {
        val card = CardView(this).apply {
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
            gravity = Gravity.CENTER_VERTICAL
            val pad = (14 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val name = nameStore.getCustomName(mac) ?: "Unknown Device"
        textCol.addView(TextView(this).apply {
            text = name
            setTextColor(getColor(R.color.text_primary))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        textCol.addView(TextView(this).apply {
            text = mac.lowercase()
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
        })

        val removeBtn = Button(this).apply {
            text = "Unblock"
            setTextColor(getColor(R.color.card_white))
            background = getDrawable(R.drawable.bg_pill_solid_green)
            setPadding(24, 8, 24, 8)
            setOnClickListener { unblockMac(mac) }
        }

        row.addView(textCol)
        row.addView(removeBtn)
        card.addView(row)
        return card
    }

    private fun unblockMac(mac: String) {
        lifecycleScope.launch {
            val success = routerAdapter.unblockDevice(mac)
            if (success) {
                Snackbar.make(layoutRules, "Unblocked $mac", Snackbar.LENGTH_SHORT).show()
                loadRules()
            } else {
                Snackbar.make(layoutRules, "Failed — check router connection", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showAddRuleDialog() {
        val input = EditText(this).apply {
            hint = "MAC address, e.g. AA:BB:CC:DD:EE:FF"
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(this)
            .setTitle("Block a MAC address")
            .setView(input)
            .setPositiveButton("Block") { _, _ ->
                val mac = input.text.toString().trim()
                if (!isValidMac(mac)) {
                    Toast.makeText(this, "Enter a valid MAC address", Toast.LENGTH_SHORT).show()
                } else {
                    lifecycleScope.launch {
                        val success = routerAdapter.blockDevice(mac)
                        if (success) {
                            Snackbar.make(layoutRules, "Blocked $mac", Snackbar.LENGTH_SHORT).show()
                            loadRules()
                        } else {
                            Snackbar.make(layoutRules, "Failed — check router connection", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isValidMac(mac: String): Boolean =
        Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(mac)
}
