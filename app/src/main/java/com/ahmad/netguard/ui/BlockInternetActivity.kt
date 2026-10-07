package com.ahmad.netguard.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.DeviceNameStore
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.launch

/** Block Internet: blocked devices (Unblock) aur baaki devices (Block). Devices wali screen se alag. */
class BlockInternetActivity : NgScreen() {

    private lateinit var names: DeviceNameStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        names = DeviceNameStore(this)
        setupScreen("Block Internet") { load() }
        load()
    }

    private fun nameOf(d: Device): String =
        names.getCustomName(d.macAddress) ?: DeviceNamer.pretty(d.displayName)

    private fun load() {
        showLoading()
        lifecycleScope.launch {
            val ad = RouterAdapterFactory.getAdapter()
            val devs = SessionKeeper.devices()
            val blocked = try { ad.getBlockedMacs() } catch (e: Exception) { emptySet<String>() }
            RouterCache.blocked = blocked.size
            render(devs, blocked)
            pull.done()
        }
    }

    private fun render(devs: List<Device>, blocked: Set<String>) {
        col.removeAllViews()

        val info = card(14)
        info.addView(
            tv("A blocked device stays connected to WiFi but has no internet access.", 13f, cSub())
        )
        add(col, info, bottom = 14)

        // ---- blocked
        val blockedDevices = ArrayList<Device>()
        for (d in devs) if (blocked.contains(d.macAddress.uppercase())) blockedDevices.add(d)
        val known = devs.map { it.macAddress.uppercase() }.toSet()
        for (m in blocked) {
            if (m !in known) {
                blockedDevices.add(Device(macAddress = m, displayName = "Blocked device", ipAddress = "—", isOnline = false, isBlocked = true))
            }
        }
        add(col, section("Blocked (${blockedDevices.size})"), bottom = 0)
        if (blockedDevices.isEmpty()) {
            val c = card(16)
            c.addView(tv("No blocked devices.", 14f, cSub()))
            add(col, c, bottom = 14)
        } else {
            for (d in blockedDevices) add(col, deviceRow(d, true), bottom = 10)
            add(col, View(this), h = dp(4), bottom = 0)
        }

        // ---- everyone else
        val others = devs.filter { !blocked.contains(it.macAddress.uppercase()) }
            .sortedByDescending { it.isOnline }
        add(col, section("Block a device"), bottom = 0)
        if (others.isEmpty()) {
            val c = card(16)
            c.addView(tv("No other devices found.", 14f, cSub()))
            add(col, c, bottom = 0)
        }
        for (d in others) add(col, deviceRow(d, false), bottom = 10)
    }

    private fun deviceRow(d: Device, isBlocked: Boolean): View {
        val c = card(14)
        val row = hrow()
        val tb = vcol()
        tb.addView(tv(nameOf(d), 15f, cText(), true))
        val status = if (d.isOnline) "Online" else "Offline"
        tb.addView(tv(status + " · " + d.ipAddress + " · " + d.macAddress, 11f, cSub()))
        row.addView(tb, LinearLayout.LayoutParams(0, wrapP, 1f))
        val btn = pill(if (isBlocked) "Unblock" else "Block", if (isBlocked) cAcc() else ThemeManager.danger(), true)
        btn.setOnClickListener {
            btn.isEnabled = false
            btn.text = "…"
            lifecycleScope.launch {
                val ad = RouterAdapterFactory.getAdapter()
                val ok = try {
                    if (isBlocked) ad.unblockDevice(d.macAddress) else ad.blockDevice(d.macAddress)
                } catch (e: Exception) {
                    false
                }
                toast(if (ok) (if (isBlocked) "Device unblocked" else "Device blocked") else "Action failed")
                load()
            }
        }
        row.addView(btn)
        c.addView(row)
        return c
    }
}
