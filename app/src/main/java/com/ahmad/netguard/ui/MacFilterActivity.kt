package com.ahmad.netguard.ui

import android.os.Bundle
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.network.DeviceNameStore
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.launch

/** Blocked MAC addresses ki list, naye theme mein. */
class MacFilterActivity : NgScreen() {

    private lateinit var names: DeviceNameStore
    private val macRegex = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        names = DeviceNameStore(this)
        setupScreen("MAC Filter") { load() }
        load()
    }

    private fun load() {
        col.removeAllViews()
        showLoading()

        lifecycleScope.launch {
            val ad = RouterAdapterFactory.getAdapter()
            val blocked = try { ad.getBlockedMacs() } catch (e: Exception) { emptySet<String>() }
            val enabled = try { ad.isFilterEnabled() } catch (e: Exception) { false }
            col.removeAllViews()

            val head = card(16)
            val top = hrow()
            val tb = vcol()
            tb.addView(tv("Blocklist filter", 16f, cText(), true))
            tb.addView(tv(if (enabled) "Active — listed devices cannot connect" else "Off — turns on when you block a device", 12f, cSub()))
            top.addView(tb, LinearLayout.LayoutParams(0, wrapP, 1f))
            top.addView(pill(if (enabled) "ON" else "OFF", if (enabled) cAcc() else cSub()))
            head.addView(top)
            add(col, head)

            val addBtn = pill("Block a MAC address", ThemeManager.danger(), true)
            addBtn.setOnClickListener {
                NgDialog.input(this@MacFilterActivity, "Block a device", "Format: AA:BB:CC:DD:EE:FF", "", "MAC address") { raw ->
                    val mac = raw.trim().replace('-', ':').uppercase()
                    if (!macRegex.matches(mac)) {
                        toast("That is not a valid MAC address")
                    } else {
                        lifecycleScope.launch {
                            val ok = try { ad.blockDevice(mac) } catch (e: Exception) { false }
                            toast(if (ok) "Device blocked" else "Could not block")
                            load()
                        }
                    }
                }
            }
            add(col, addBtn, bottom = 14)

            add(col, section("Blocked devices (${blocked.size})"), bottom = 0)
            if (blocked.isEmpty()) {
                val c = card(16)
                c.addView(tv("No blocked devices.", 14f, cSub()))
                add(col, c, bottom = 0)
            }
            for (mac in blocked.sorted()) {
                val c = card(14)
                val row = hrow()
                val t = vcol()
                t.addView(tv(names.getCustomName(mac) ?: "Blocked device", 15f, cText(), true))
                t.addView(tv(mac, 12f, cSub()))
                row.addView(t, LinearLayout.LayoutParams(0, wrapP, 1f))
                val un = pill("Unblock", cAcc())
                un.setOnClickListener {
                    un.isEnabled = false
                    lifecycleScope.launch {
                        val ok = try { ad.unblockDevice(mac) } catch (e: Exception) { false }
                        toast(if (ok) "Device unblocked" else "Could not unblock")
                        load()
                    }
                }
                row.addView(un)
                c.addView(row)
                add(col, c, bottom = 10)
            }
            pull.done()
        }
    }
}
