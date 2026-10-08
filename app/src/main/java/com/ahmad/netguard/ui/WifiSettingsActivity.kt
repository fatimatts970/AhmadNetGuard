package com.ahmad.netguard.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.launch

/** Sirf main WiFi (name + password) aur Restart Router. Guest ke liye alag screen hai. */
class WifiSettingsActivity : NgScreen() {

    private lateinit var ssidEdit: EditText
    private lateinit var passEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreen("Wi-Fi Password") { loadSsid() }
        build()
        loadSsid()
    }

    private fun build() {
        add(col, section("Main WiFi"), bottom = 0)
        val main = card(16)
        main.addView(fieldLabel("Network name (SSID)"))
        val (b1, e1) = inputField("Network name")
        ssidEdit = e1
        main.addView(b1)
        main.addView(fieldLabel("New password (8+ characters)"))
        val (b2, e2) = inputField("New password", password = true)
        passEdit = e2
        main.addView(b2)
        val save = pill("Save changes", cAcc(), true)
        save.textSize = 14f
        save.setOnClickListener { saveMain(save) }
        val lp = LinearLayout.LayoutParams(matchP, wrapP)
        lp.topMargin = dp(14)
        main.addView(save, lp)
        add(col, main, bottom = 6)
        add(col, tv("Saving changes restarts the WiFi for a few seconds, so your phone may disconnect for a moment.", 12f, cSub()), bottom = 18)

        add(col, section("Restart Router"), bottom = 0)
        val reboot = card(14)
        val row = hrow()
        val tb = vcol()
        tb.addView(tv("Restart Router", 16f, ThemeManager.danger(), true))
        tb.addView(tv("All devices lose internet for a short time", 12f, cSub()))
        row.addView(tb, LinearLayout.LayoutParams(0, wrapP, 1f))
        row.addView(ic(NgIcon.POWER, ThemeManager.danger(), 26))
        reboot.addView(row)
        reboot.setOnClickListener {
            NgDialog.confirm(this, "Restart Router", "All connected devices will lose internet for a short time. Continue?", "Restart", true) {
                lifecycleScope.launch {
                    val ok = try { RouterAdapterFactory.getAdapter().restartRouter() } catch (e: Exception) { false }
                    toast(if (ok) "Router is restarting…" else "Restart failed")
                    if (ok) SessionKeeper.refreshAfterWrite()
                }
            }
        }
        add(col, reboot, bottom = 0)
    }

    private fun loadSsid() {
        lifecycleScope.launch {
            val s = try { RouterAdapterFactory.getAdapter().getWifiSsidName() } catch (e: Exception) { null }
            if (!s.isNullOrBlank() && ssidEdit.text.isNullOrBlank()) ssidEdit.setText(TextFix.decode(s))
            pull.done()
        }
    }

    private fun saveMain(btn: android.widget.TextView) {
        val ssid = ssidEdit.text.toString().trim()
        val pass = passEdit.text.toString().trim()
        if (ssid.isEmpty() || pass.length < 8) {
            toast("Enter a name and a password of 8+ characters")
            return
        }
        btn.isEnabled = false
        btn.text = "Saving…"
        lifecycleScope.launch {
            val ad = RouterAdapterFactory.getAdapter()
            var ok = try { ad.updateWifiSettings(ssid, pass) } catch (e: Exception) { false }
            if (!ok && SessionKeeper.relogin(force = true)) {
                ok = try { ad.updateWifiSettings(ssid, pass) } catch (e: Exception) { false }
            }
            btn.isEnabled = true
            btn.text = "Save changes"
            toast(if (ok) "WiFi updated" else "Could not update WiFi")
            if (ok) {
                passEdit.setText("")
                SessionKeeper.refreshAfterWrite()
            }
        }
    }
}
