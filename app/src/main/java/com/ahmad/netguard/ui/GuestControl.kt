package com.ahmad.netguard.ui

import android.content.Context
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.delay

/** Guest WiFi ki last known on/off state (Dashboard aur Guest screen dono isi se sync rehte hain). */
object GuestState {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("ng_guest", Context.MODE_PRIVATE)
    fun isOn(c: Context): Boolean = p(c).getBoolean("on", false)
    fun setOn(c: Context, v: Boolean) {
        p(c).edit().putBoolean("on", v).apply()
    }

    /** Router se asli halat padh kar save karo. Null = padh nahi saka. */
    suspend fun sync(c: Context): GuestRouter.State? {
        val st = GuestRouter.read()
        if (st != null) setOn(c, st.enabled)
        return st
    }
}

/** Router se guest WiFi (WLANConfiguration.2) ki asli halat. */
object GuestRouter {

    class State(val enabled: Boolean, val ssid: String)

    suspend fun read(): State? {
        val page = try {
            com.ahmad.netguard.network.PageReader.fetchFirst(
                listOf("/html/amp/wlaninfo/wlaninfo.asp", "/amp/wlaninfo/wlaninfo.asp")
            )
        } catch (e: Exception) {
            null
        }
        if (page == null) return null
        for (c in page.scraped.calls) {
            if (!c.name.startsWith("stWlan")) continue
            val domain = c.args.firstOrNull() ?: continue
            if (!domain.endsWith("WLANConfiguration.2")) continue
            val enable = c.args.getOrNull(1)
            if (enable == "1" || enable == "0") {
                return State(enable == "1", TextFix.decode(c.args.getOrNull(3)))
            }
        }
        return null
    }
}

object GuestControl {

    private fun friendly(raw: String): String = when {
        raw.contains("token", true) || raw.contains("logged out", true) ->
            "Session expired. Please log in again."
        else -> raw.removePrefix("FAIL: ").take(100)
    }

    /**
     * null = kamyab (router se confirm), warna error ka text.
     * Pehle likhta hai, router ka WiFi restart hone ka intezar karta hai, naya session leta hai,
     * aur router se wapas padh kar check karta hai ke change sach mein laga ya nahi.
     */
    suspend fun set(ctx: Context, ssid: String, key: String, enable: Boolean): String? =
        SessionKeeper.withOp {
            SessionKeeper.cancelPendingRefresh()
            val ad = RouterAdapterFactory.getAdapter()

            suspend fun attempt(): String =
                try {
                    ad.setGuestWifiDiagnostic(ssid, key, enable)
                } catch (e: Exception) {
                    "FAIL: " + (e.message ?: "unknown error")
                }

            var result = attempt()
            var ok = result == "SUCCESS" || result == "APPLIED"
            var tries = 0
            while (!ok && tries < 2) {
                tries++
                if (!SessionKeeper.ensureSession(3)) break
                result = attempt()
                ok = result == "SUCCESS" || result == "APPLIED"
            }
            if (!ok) return@withOp friendly(result)

            // WiFi radio restart hota hai: thoda ruko, naya session lo, phir router se check karo
            delay(7_000)
            SessionKeeper.ensureSession(4)
            val st = GuestRouter.read()
            if (st == null) {
                GuestState.setOn(ctx, enable)
                return@withOp null
            }
            GuestState.setOn(ctx, st.enabled)
            if (st.enabled != enable) {
                return@withOp if (enable) "The router did not turn the guest WiFi on." else "The router did not turn the guest WiFi off."
            }
            val want = TextFix.plain(ssid)
            val got = TextFix.plain(st.ssid)
            if (enable && want.isNotEmpty() && got.isNotEmpty() && want != got) {
                return@withOp "The router kept the old name \"" + st.ssid + "\". Try a shorter name."
            }
            null
        }
}
