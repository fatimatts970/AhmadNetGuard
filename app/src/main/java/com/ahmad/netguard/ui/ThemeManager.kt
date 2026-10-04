package com.ahmad.netguard.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * AHMAD WiFi Manager — theme settings (dark mode, accent colour, card style,
 * corner style, 3D effect, dashboard widgets). Sab kuch SharedPreferences mein save hota hai.
 */
object ThemeManager {

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("ng_theme", Context.MODE_PRIVATE)
        }
    }

    private fun p(): SharedPreferences =
        prefs ?: throw IllegalStateException("ThemeManager.init() not called")

    val accents: IntArray = intArrayOf(
        Color.parseColor("#1E7BFF"), // Blue
        Color.parseColor("#7C3AED"), // Purple
        Color.parseColor("#16A34A"), // Green
        Color.parseColor("#F97316"), // Orange
        Color.parseColor("#DB2777"), // Rose
        Color.parseColor("#0E9AA7")  // Teal
    )
    val accentNames: List<String> = listOf("Blue", "Purple", "Green", "Orange", "Rose", "Teal")

    // Dashboard tiles jo on/off ho sakte hain (key to title)
    val widgetKeys: List<Pair<String, String>> = listOf(
        "wan" to "WAN Status",
        "devices" to "Devices",
        "optical" to "Optical Info",
        "ports" to "Ethernet Ports",
        "devinfo" to "Device Info",
        "usage" to "Usage",
        "voip" to "VoIP Status",
        "wifipass" to "Wi-Fi Password",
        "guest" to "Guest",
        "macfilter" to "MAC Filter",
        "parental" to "Parental Control",
        "blocknet" to "Block Internet"
    )

    var dark: Boolean
        get() = p().getBoolean("dark", false)
        set(v) { p().edit().putBoolean("dark", v).apply() }

    var accentIndex: Int
        get() = p().getInt("accent", 5)
        set(v) { p().edit().putInt("accent", v).apply() }

    /** 0 Classic, 1 One UI, 2 Ice, 3 Halo */
    var style: Int
        get() = p().getInt("style", 3)
        set(v) { p().edit().putInt("style", v).apply() }

    /** 0 Rounded, 1 Compact, 2 Square */
    var corner: Int
        get() = p().getInt("corner", 0)
        set(v) { p().edit().putInt("corner", v).apply() }

    var effect3d: Boolean
        get() = p().getBoolean("effect3d", false)
        set(v) { p().edit().putBoolean("effect3d", v).apply() }

    var fingerprintOn: Boolean
        get() = p().getBoolean("fingerprint", true)
        set(v) { p().edit().putBoolean("fingerprint", v).apply() }

    var alertsOn: Boolean
        get() = p().getBoolean("alerts", true)
        set(v) { p().edit().putBoolean("alerts", v).apply() }

    var autoSpeed: Boolean
        get() = p().getBoolean("autospeed", true)
        set(v) { p().edit().putBoolean("autospeed", v).apply() }

    fun fingerprint(context: Context): Boolean {
        init(context)
        return fingerprintOn
    }

    fun widgetOn(key: String): Boolean = p().getBoolean("w_$key", true)

    fun setWidget(key: String, on: Boolean) {
        p().edit().putBoolean("w_$key", on).apply()
    }

    fun accent(): Int = accents[accentIndex.coerceIn(0, accents.size - 1)]

    fun bg(): Int {
        val base = if (dark) Color.parseColor("#0E1512") else Color.parseColor("#EEF7F2")
        return ColorUtils.blendARGB(base, accent(), 0.05f)
    }

    fun surface(): Int = if (dark) Color.parseColor("#17211D") else Color.WHITE

    fun bar(): Int = ColorUtils.blendARGB(surface(), accent(), 0.06f)

    fun text(): Int = if (dark) Color.parseColor("#E9F2EE") else Color.parseColor("#0F172A")

    fun sub(): Int = if (dark) Color.parseColor("#93A79E") else Color.parseColor("#64748B")

    fun border(): Int = if (dark) Color.parseColor("#26352F") else Color.parseColor("#DDE7E1")

    fun danger(): Int = Color.parseColor("#E5484D")

    fun radiusDp(): Float = when (corner) {
        0 -> 24f
        1 -> 14f
        else -> 4f
    }
}
