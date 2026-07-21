package com.ahmad.netguard.network

import android.content.Context

class DeviceNameStore(context: Context) {
    private val prefs = context.getSharedPreferences("device_names", Context.MODE_PRIVATE)

    fun getCustomName(mac: String): String? = prefs.getString(mac, null)

    fun setCustomName(mac: String, name: String) {
        prefs.edit().putString(mac, name).apply()
    }
}
