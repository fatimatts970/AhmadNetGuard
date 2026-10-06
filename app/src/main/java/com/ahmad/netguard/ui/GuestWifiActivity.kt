package com.ahmad.netguard.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.launch

/** Guest WiFi: ON/OFF pill, naya guest network banana, saved networks (use / edit / delete). */
class GuestWifiActivity : NgScreen() {

    private lateinit var creds: RouterCredentialStore
    private lateinit var ssidEdit: EditText
    private lateinit var passEdit: EditText
    private lateinit var listBox: LinearLayout
    private lateinit var togglePill: TextView
    private lateinit var statusText: TextView
    private var editing: String? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        creds = RouterCredentialStore(this)
        setupScreen("Guest WiFi") {
            refreshHeader()
            pull.postDelayed({ pull.done() }, 500)
        }
        build()
    }

    override fun onResume() {
        super.onResume()
        if (::togglePill.isInitialized) refreshHeader()
    }

    private fun build() {
        // ---- header card with ON/OFF pill
        val head = card(16)
        val row = hrow()
        row.addView(ic(NgIcon.WIFI, cAcc(), 28))
        val tb = vcol()
        tb.setPadding(dp(12), 0, 0, 0)
        tb.addView(tv("Guest WiFi", 18f, cText(), true))
        statusText = tv("", 12f, cSub())
        tb.addView(statusText)
        row.addView(tb, LinearLayout.LayoutParams(0, wrapP, 1f))
        togglePill = pill("OFF", cSub())
        togglePill.setOnClickListener { toggle() }
        row.addView(togglePill)
        head.addView(row)
        add(col, head, bottom = 14)

        // ---- add / edit
        add(col, section("Guest network"), bottom = 0)
        val form = card(16)
        form.addView(fieldLabel("Guest network name"))
        val (b1, e1) = inputField("Guest name")
        ssidEdit = e1
        form.addView(b1)
        form.addView(fieldLabel("Guest password (8+ characters)"))
        val (b2, e2) = inputField("Guest password", password = true)
        passEdit = e2
        form.addView(b2)
        val save = pill("Save & turn on", cAcc(), true)
        save.textSize = 14f
        save.setOnClickListener { saveNew(save) }
        val lp = LinearLayout.LayoutParams(matchP, wrapP)
        lp.topMargin = dp(14)
        form.addView(save, lp)
        add(col, form, bottom = 14)

        add(col, section("Saved networks"), bottom = 0)
        listBox = vcol()
        add(col, listBox, bottom = 0)

        refreshHeader()
        renderList()
    }

    private fun refreshHeader() {
        val on = GuestState.isOn(this)
        val active = creds.getGuestSsid()
        togglePill.text = if (on) "ON" else "OFF"
        val color = if (on) cAcc() else cSub()
        togglePill.setTextColor(if (on) android.graphics.Color.WHITE else cSub())
        val g = android.graphics.drawable.GradientDrawable()
        g.cornerRadius = dpf(40f)
        if (on) g.setColor(color) else {
            g.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 36))
            g.setStroke(dp(1), androidx.core.graphics.ColorUtils.setAlphaComponent(color, 120))
        }
        togglePill.background = g
        statusText.text = when {
            active.isBlank() -> "No guest network saved yet"
            on -> "On · $active"
            else -> "Off · $active"
        }
    }

    private fun toggle() {
        if (busy) return
        val ssid = creds.getGuestSsid()
        val key = creds.getGuestKey()
        if (ssid.isBlank() || key.length < 8) {
            toast("Save a guest network first")
            return
        }
        val target = !GuestState.isOn(this)
        busy = true
        togglePill.text = "…"
        lifecycleScope.launch {
            val err = GuestControl.set(this@GuestWifiActivity, ssid, key, target)
            busy = false
            refreshHeader()
            renderList()
            toast(if (err == null) (if (target) "Guest WiFi is on" else "Guest WiFi is off") else "Failed: " + err.take(70))
        }
    }

    private fun saveNew(btn: TextView) {
        val ssid = ssidEdit.text.toString().trim()
        val pass = passEdit.text.toString().trim()
        if (ssid.isEmpty() || pass.length < 8) {
            toast("Enter a name and a password of 8+ characters")
            return
        }
        btn.isEnabled = false
        btn.text = "Saving…"
        lifecycleScope.launch {
            val err = GuestControl.set(this@GuestWifiActivity, ssid, pass, true)
            btn.isEnabled = true
            btn.text = "Save & turn on"
            if (err == null) {
                creds.saveGuestProfile(ssid, pass, editing)
                creds.saveGuestWifi(ssid, pass)
                editing = null
                ssidEdit.setText("")
                passEdit.setText("")
                refreshHeader()
                renderList()
                toast("Guest WiFi is on")
            } else {
                toast("Failed: " + err.take(80))
            }
        }
    }

    private fun renderList() {
        listBox.removeAllViews()
        val profiles = creds.getGuestProfiles()
        val active = creds.getGuestSsid()
        if (profiles.isEmpty()) {
            add(listBox, tv("No saved guest networks yet.", 13f, cSub()), bottom = 0)
            return
        }
        for ((ssid, pass) in profiles) {
            val isActive = ssid == active
            val c = card(14)
            val top = hrow()
            top.addView(tv(ssid, 16f, cText(), true), LinearLayout.LayoutParams(0, wrapP, 1f))
            if (isActive) top.addView(pill(if (GuestState.isOn(this)) "Active · ON" else "Active · OFF", cAcc()))
            c.addView(top)

            var shown = false
            val pw = tv("Password  ••••••••   (tap to show)", 12f, cSub())
            pw.setPadding(0, dp(6), 0, dp(10))
            pw.setOnClickListener {
                shown = !shown
                pw.text = if (shown) "Password  $pass" else "Password  ••••••••   (tap to show)"
            }
            c.addView(pw)

            val actions = hrow()
            if (!isActive) {
                val use = pill("Use", cAcc(), true)
                use.setOnClickListener { useProfile(ssid, pass) }
                val up = LinearLayout.LayoutParams(wrapP, wrapP)
                up.marginEnd = dp(10)
                actions.addView(use, up)
            }
            val edit = pill("Edit", cAcc())
            edit.setOnClickListener {
                editing = ssid
                ssidEdit.setText(ssid)
                passEdit.setText(pass)
                toast("Change the values above, then tap Save & turn on")
            }
            val del = pill("Delete", ThemeManager.danger())
            del.setOnClickListener { confirmDelete(ssid, pass, isActive) }
            val ep = LinearLayout.LayoutParams(wrapP, wrapP)
            ep.marginEnd = dp(10)
            actions.addView(edit, ep)
            actions.addView(del)
            c.addView(actions)
            add(listBox, c, bottom = 10)
        }
    }

    private fun useProfile(ssid: String, pass: String) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            val err = GuestControl.set(this@GuestWifiActivity, ssid, pass, true)
            busy = false
            if (err == null) {
                creds.saveGuestWifi(ssid, pass)
                toast("Now using \"$ssid\"")
            } else {
                toast("Failed: " + err.take(70))
            }
            refreshHeader()
            renderList()
        }
    }

    private fun removeLocally(ssid: String, wasActive: Boolean) {
        creds.deleteGuestProfile(ssid)
        if (wasActive) {
            creds.saveGuestWifi("", "")
            GuestState.setOn(this, false)
        }
        if (editing == ssid) editing = null
        refreshHeader()
        renderList()
    }

    private fun confirmDelete(ssid: String, pass: String, isActive: Boolean) {
        val msg = if (isActive && GuestState.isOn(this))
            "\"$ssid\" is on. Turn it off and remove it?"
        else
            "Remove \"$ssid\" from the saved list?"
        NgDialog.confirm(this, "Delete guest network", msg, "Delete", true) {
            if (!(isActive && GuestState.isOn(this))) {
                removeLocally(ssid, isActive)
                toast("Removed")
                return@confirm
            }
            lifecycleScope.launch {
                val err = GuestControl.set(this@GuestWifiActivity, ssid, pass, false)
                if (err == null) {
                    removeLocally(ssid, true)
                    toast("Guest network turned off and removed")
                } else {
                    NgDialog.confirm(
                        this@GuestWifiActivity, "Could not turn it off",
                        err.take(120) + "\n\nRemove it from the list anyway? The router's guest WiFi may stay on.",
                        "Remove anyway", true
                    ) {
                        removeLocally(ssid, true)
                    }
                }
            }
        }
    }
}
