package com.ahmad.netguard.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import android.view.Gravity
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.launch

/** Guest WiFi: naya network banana aur saved networks (Active / Edit / Delete / ON-OFF). */
class GuestWifiActivity : NgScreen() {

    private lateinit var creds: RouterCredentialStore
    private lateinit var ssidEdit: EditText
    private lateinit var passEdit: EditText
    private lateinit var listBox: LinearLayout
    private lateinit var statusText: TextView
    private var editing: String? = null
    private var routerSsid: String? = null
    private var busy = false
    private val green = Color.parseColor("#16A34A")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        creds = RouterCredentialStore(this)
        setupScreen("Guest WiFi") { syncFromRouter() }
        build()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) syncFromRouter()
    }

    /** Router se asli on/off aur naam padho (local yaad par bharosa nahi). */
    private fun syncFromRouter() {
        refreshHeader()
        renderList()
        lifecycleScope.launch {
            val st = GuestState.sync(this@GuestWifiActivity)
            routerSsid = st?.ssid
            refreshHeader()
            renderList()
            pull.done()
        }
    }

    private fun build() {
        val head = card(16)
        val row = hrow()
        row.addView(ic(NgIcon.WIFI, cAcc(), 28))
        val tb = vcol()
        tb.setPadding(dp(12), 0, 0, 0)
        tb.addView(tv("Guest WiFi", 18f, cText(), true))
        statusText = tv("", 12f, cSub())
        tb.addView(statusText)
        row.addView(tb, LinearLayout.LayoutParams(0, wrapP, 1f))
        head.addView(row)
        add(col, head, bottom = 14)

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
        val shownName = if (on && !routerSsid.isNullOrBlank()) routerSsid!! else creds.getGuestSsid()
        statusText.text = when {
            shownName.isBlank() -> "No guest network saved yet"
            on -> "On · $shownName"
            else -> "Off · $shownName"
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
        renderList()
        toast("Applying… the router restarts its WiFi, this takes about 10 seconds")
        lifecycleScope.launch {
            val err = GuestControl.set(this@GuestWifiActivity, ssid, key, target)
            busy = false
            if (err == null) routerSsid = if (target) ssid else routerSsid
            refreshHeader()
            renderList()
            toast(if (err == null) (if (target) "Guest WiFi is on" else "Guest WiFi is off") else err)
        }
    }

    private fun saveNew(btn: TextView) {
        val ssid = ssidEdit.text.toString().trim()
        val pass = passEdit.text.toString().trim()
        if (ssid.isEmpty() || pass.length < 8) {
            toast("Enter a name and a password of 8+ characters")
            return
        }
        if (ssid.toByteArray(Charsets.UTF_8).size > 32) {
            toast("Name is too long: 32 bytes maximum (each emoji uses 4)")
            return
        }
        if (pass.length > 63) {
            toast("Password can be 63 characters at most")
            return
        }
        btn.isEnabled = false
        btn.text = "Applying…"
        toast("Applying… the router restarts its WiFi, this takes about 10 seconds")
        lifecycleScope.launch {
            val err = GuestControl.set(this@GuestWifiActivity, ssid, pass, true)
            btn.isEnabled = true
            btn.text = "Save & turn on"
            if (err == null) {
                routerSsid = ssid
                creds.saveGuestProfile(ssid, pass, editing)
                creds.saveGuestWifi(ssid, pass)
                editing = null
                ssidEdit.setText("")
                passEdit.setText("")
                refreshHeader()
                renderList()
                toast("Guest WiFi is on")
            } else {
                toast(err)
            }
        }
    }

    private fun fixedPill(text: String, fg: Int, filledColor: Int?, outline: Int?): TextView {
        val t = tv(text, 12f, fg, true)
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(9), 0, dp(9))
        val g = GradientDrawable()
        g.cornerRadius = dpf(40f)
        if (filledColor != null) g.setColor(filledColor)
        else {
            g.setColor(ColorUtils.setAlphaComponent(outline ?: cSub(), 30))
            g.setStroke(dp(1), ColorUtils.setAlphaComponent(outline ?: cSub(), 130))
        }
        t.background = g
        t.layoutParams = LinearLayout.LayoutParams(dp(96), wrapP)
        return t
    }

    private fun renderList() {
        listBox.removeAllViews()
        val profiles = creds.getGuestProfiles()
        val active = creds.getGuestSsid()
        if (profiles.isEmpty()) {
            add(listBox, tv("No saved guest networks yet.", 13f, cSub()), bottom = 0)
            return
        }
        val isOn = GuestState.isOn(this)
        for ((ssid, pass) in profiles) {
            val isActive = ssid == active
            val c = card(14)

            // row 1: name + Active
            val top = hrow()
            top.addView(tv(ssid, 16f, cText(), true), LinearLayout.LayoutParams(0, wrapP, 1f))
            if (isActive) top.addView(fixedPill("Active", Color.WHITE, green, null))
            c.addView(top)

            // row 2: password + ON/OFF (exactly under Active) or Use
            val mid = hrow()
            var shown = false
            val pw = tv("Password  ••••••••   (tap to show)", 12f, cSub())
            pw.setOnClickListener {
                shown = !shown
                pw.text = if (shown) "Password  $pass" else "Password  ••••••••   (tap to show)"
            }
            mid.addView(pw, LinearLayout.LayoutParams(0, wrapP, 1f))
            if (isActive) {
                val label = if (busy) "…" else if (isOn) "ON" else "OFF"
                val sw = if (isOn && !busy) fixedPill(label, Color.WHITE, green, null)
                else fixedPill(label, cSub(), null, cSub())
                sw.setOnClickListener { toggle() }
                mid.addView(sw)
            } else {
                val use = fixedPill("Use", Color.WHITE, cAcc(), null)
                use.setOnClickListener { useProfile(ssid, pass) }
                mid.addView(use)
            }
            val mlp = LinearLayout.LayoutParams(matchP, wrapP)
            mlp.topMargin = dp(10)
            c.addView(mid, mlp)

            // row 3: Edit + Delete
            val actions = hrow()
            val edit = pill("Edit", cAcc())
            edit.setOnClickListener {
                editing = ssid
                ssidEdit.setText(ssid)
                passEdit.setText(pass)
                toast("Change the values above, then tap Save & turn on")
            }
            val ep = LinearLayout.LayoutParams(wrapP, wrapP)
            ep.marginEnd = dp(10)
            actions.addView(edit, ep)
            val del = pill("Delete", ThemeManager.danger())
            del.setOnClickListener { confirmDelete(ssid, pass, isActive) }
            actions.addView(del)
            val alp = LinearLayout.LayoutParams(wrapP, wrapP)
            alp.topMargin = dp(12)
            c.addView(actions, alp)
            add(listBox, c, bottom = 10)
        }
    }

    private fun useProfile(ssid: String, pass: String) {
        if (busy) return
        busy = true
        renderList()
        toast("Applying… the router restarts its WiFi, this takes about 10 seconds")
        lifecycleScope.launch {
            val err = GuestControl.set(this@GuestWifiActivity, ssid, pass, true)
            busy = false
            if (err == null) {
                creds.saveGuestWifi(ssid, pass)
                routerSsid = ssid
                toast("Now using \"$ssid\"")
            } else {
                toast(err)
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
        val msg = "Remove \"$ssid\"? If it is on, it will be turned off on the router first."
        NgDialog.confirm(this, "Delete guest network", msg, "Delete", true) {
            lifecycleScope.launch {
                val st = if (isActive) GuestState.sync(this@GuestWifiActivity) else null
                val routerOn = if (isActive) (st?.enabled ?: GuestState.isOn(this@GuestWifiActivity)) else false
                if (!routerOn) {
                    removeLocally(ssid, isActive)
                    toast("Removed")
                    return@launch
                }
                busy = true
                renderList()
                toast("Turning it off… about 10 seconds")
                val err = GuestControl.set(this@GuestWifiActivity, ssid, pass, false)
                busy = false
                if (err == null) {
                    removeLocally(ssid, true)
                    toast("Guest network turned off and removed")
                } else {
                    renderList()
                    NgDialog.confirm(
                        this@GuestWifiActivity, "Could not turn it off",
                        err + "\n\nRemove it from the list anyway? The router's guest WiFi may stay on.",
                        "Remove anyway", true
                    ) {
                        removeLocally(ssid, true)
                    }
                }
            }
        }
    }
}
