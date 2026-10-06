package com.ahmad.netguard.ui

import android.content.Context
import com.ahmad.netguard.network.RouterAdapterFactory

/** Guest WiFi ki on/off state (Dashboard aur Guest screen dono isi se sync rehte hain). */
object GuestState {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("ng_guest", Context.MODE_PRIVATE)
    fun isOn(c: Context): Boolean = p(c).getBoolean("on", false)
    fun setOn(c: Context, v: Boolean) {
        p(c).edit().putBoolean("on", v).apply()
    }
}

object GuestControl {

    /** null = kamyab, warna error ka text. Fail hone par ek baar naya login karke dobara koshish karta hai. */
    suspend fun set(ctx: Context, ssid: String, key: String, enable: Boolean): String? {
        val ad = RouterAdapterFactory.getAdapter()
        suspend fun attempt(): String =
            try {
                ad.setGuestWifiDiagnostic(ssid, key, enable)
            } catch (e: Exception) {
                "FAIL: " + (e.message ?: "unknown error")
            }

        var result = attempt()
        var ok = result == "SUCCESS" || result == "APPLIED"
        if (!ok && SessionKeeper.relogin(force = true)) {
            result = attempt()
            ok = result == "SUCCESS" || result == "APPLIED"
        }
        if (ok) {
            GuestState.setOn(ctx, enable)
            SessionKeeper.refreshAfterWrite()
            return null
        }
        return result
    }
}
