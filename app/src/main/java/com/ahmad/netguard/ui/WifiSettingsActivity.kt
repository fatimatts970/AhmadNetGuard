package com.ahmad.netguard.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.launch

/** Main WiFi + Guest WiFi + reboot, naye theme mein. */
class WifiSettingsActivity : NgScreen() {

    private lateinit var creds: RouterCredentialStore
    private lateinit var ssidEdit: EditText
    private lateinit var passEdit: EditText
    private lateinit var guestSsidEdit: EditText
    private lateinit var guestPassEdit: EditText
    private lateinit var guestList: LinearLayout
    private var editingGuest: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        creds = RouterCredentialStore(this)
        setupScreen("Wi-Fi Settings") { loadSsid() }
        build()
        loadSsid()
    }

    private fun build() {
        // ---- main wifi
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
        add(col, tv("Saving changes may disconnect your phone from the WiFi for a moment.", 12f, cSub()), bottom = 14)

        // ---- guest wifi
        add(col, section("Guest WiFi"), bottom = 0)
        val guest = card(16)
        guest.addView(fieldLabel("Guest network name"))
        val (b3, e3) = inputField("Guest name")
        guestSsidEdit = e3
        guest.addView(b3)
        guest.addView(fieldLabel("Guest password (8+ characters)"))
        val (b4, e4) = inputField("Guest password", password = true)
        guestPassEdit = e4
        guest.addView(b4)
        val gsave = pill("Save & turn on", cAcc(), true)
        gsave.setOnClickListener { saveGuest(gsave) }
        val glp = LinearLayout.LayoutParams(matchP, wrapP)
        glp.topMargin = dp(14)
        guest.addView(gsave, glp)
        add(col, guest, bottom = 12)

        guestList = vcol()
        add(col, guestList, bottom = 14)
        renderGuests()

        // ---- router
        add(col, section("Router"), bottom = 0)
        val reboot = card(14)
        val row = hrow()
        val tb = vcol()
        tb.addView(tv("Reboot modem", 15f, ThemeManager.danger(), true))
        tb.addView(tv("All devices lose internet for a short time", 12f, cSub()))
        row.addView(tb, LinearLayout.LayoutParams(0, wrapP, 1f))
        row.addView(ic(NgIcon.POWER, ThemeManager.danger(), 24))
        reboot.addView(row)
        reboot.setOnClickListener {
            NgDialog.confirm(this, "Reboot modem", "All connected devices will lose internet for a short time. Continue?", "Reboot", true) {
                lifecycleScope.launch {
                    val ok = try { RouterAdapterFactory.getAdapter().restartRouter() } catch (e: Exception) { false }
                    toast(if (ok) "Modem is rebooting…" else "Reboot failed")
                }
            }
        }
        add(col, reboot, bottom = 0)
    }

    private fun loadSsid() {
        lifecycleScope.launch {
            val s = try { RouterAdapterFactory.getAdapter().getWifiSsidName() } catch (e: Exception) { null }
            if (!s.isNullOrBlank() && ssidEdit.text.isNullOrBlank()) ssidEdit.setText(s)
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
            val ok = try { RouterAdapterFactory.getAdapter().updateWifiSettings(ssid, pass) } catch (e: Exception) { false }
            btn.isEnabled = true
            btn.text = "Save changes"
            toast(if (ok) "WiFi updated" else "Could not update WiFi")
            if (ok) passEdit.setText("")
        }
    }

    private fun saveGuest(btn: android.widget.TextView) {
        val ssid = guestSsidEdit.text.toString().trim()
        val pass = guestPassEdit.text.toString().trim()
        if (ssid.isEmpty() || pass.length < 8) {
            toast("Enter a name and a password of 8+ characters")
            return
        }
        btn.isEnabled = false
        btn.text = "Saving…"
        lifecycleScope.launch {
            val result = try {
                RouterAdapterFactory.getAdapter().setGuestWifiDiagnostic(ssid, pass, true)
            } catch (e: Exception) {
                "Error: " + (e.message ?: "unknown")
            }
            btn.isEnabled = true
            btn.text = "Save & turn on"
            val ok = result == "SUCCESS" || result == "APPLIED"
            if (ok) {
                creds.saveGuestProfile(ssid, pass, editingGuest)
                creds.saveGuestWifi(ssid, pass)
                editingGuest = null
                guestSsidEdit.setText("")
                guestPassEdit.setText("")
                renderGuests()
                toast("Guest WiFi is on")
            } else {
                toast("Guest WiFi failed: " + result.take(80))
            }
        }
    }

    private fun renderGuests() {
        guestList.removeAllViews()
        val profiles = creds.getGuestProfiles()
        val active = creds.getGuestSsid()
        if (profiles.isEmpty()) {
            add(guestList, tv("No saved guest networks yet.", 13f, cSub()), bottom = 0)
            return
        }
        for ((ssid, pass) in profiles) {
            val c = card(14)
            val top = hrow()
            top.addView(tv(ssid, 15f, cText(), true), LinearLayout.LayoutParams(0, wrapP, 1f))
            if (ssid == active) top.addView(pill("Active", cAcc()))
            c.addView(top)

            var shown = false
            val pw = tv("Password  ••••••••", 12f, cSub())
            pw.setPadding(0, dp(6), 0, dp(10))
            pw.setOnClickListener {
                shown = !shown
                pw.text = if (shown) "Password  $pass" else "Password  ••••••••"
            }
            c.addView(pw)

            val actions = hrow()
            val edit = pill("Edit", cAcc())
            edit.setOnClickListener {
                editingGuest = ssid
                guestSsidEdit.setText(ssid)
                guestPassEdit.setText(pass)
                toast("Edit the values above, then Save")
            }
            val off = pill("Turn off & delete", ThemeManager.danger())
            off.setOnClickListener {
                NgDialog.confirm(this, "Delete guest network", "Turn off and remove \"$ssid\"?", "Delete", true) {
                    lifecycleScope.launch {
                        val r = try {
                            RouterAdapterFactory.getAdapter().setGuestWifiDiagnostic(ssid, pass, false)
                        } catch (e: Exception) {
                            "Error"
                        }
                        val ok = r == "SUCCESS" || r == "APPLIED"
                        if (ok) {
                            creds.deleteGuestProfile(ssid)
                            renderGuests()
                            toast("Guest network removed")
                        } else {
                            toast("Could not turn it off")
                        }
                    }
                }
            }
            val p = LinearLayout.LayoutParams(wrapP, wrapP)
            p.marginEnd = dp(10)
            actions.addView(edit, p)
            actions.addView(off)
            c.addView(actions)
            add(guestList, c, bottom = 10)
        }
    }
}
