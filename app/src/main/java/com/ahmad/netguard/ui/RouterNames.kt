package com.ahmad.netguard.ui

import android.content.Context

/** Har router IP ka asli model naam yaad rakhta hai (login ke baad save hota hai). */
object RouterNames {

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ng_router_names", Context.MODE_PRIVATE)

    fun label(model: String): String {
        val m = model.trim()
        return if (m.isEmpty()) "" else if (m.startsWith("Huawei", true)) m else "Huawei $m"
    }

    fun save(c: Context, ip: String, model: String) {
        val l = label(model)
        if (ip.isNotBlank() && l.isNotBlank()) prefs(c).edit().putString(ip, l).apply()
    }

    fun get(c: Context, ip: String): String? = prefs(c).getString(ip, null)
}
