package com.ahmad.netguard.network

import android.content.Context
import org.json.JSONObject

class DeviceNameStore(context: Context) {
    private val prefs = context.getSharedPreferences("device_names", Context.MODE_PRIVATE)

    fun getCustomName(mac: String): String? = prefs.getString(mac, null)

    fun setCustomName(mac: String, name: String) {
        prefs.edit().putString(mac, name).apply()
    }

    fun exportAllAsJson(): String {
        val json = JSONObject()
        for ((mac, name) in prefs.all) {
            if (name is String) json.put(mac, name)
        }
        return json.toString(2)
    }

    fun importFromJson(jsonText: String): Int {
        val json = JSONObject(jsonText)
        val editor = prefs.edit()
        var count = 0
        json.keys().forEach { mac ->
            editor.putString(mac, json.getString(mac))
            count++
        }
        editor.apply()
        return count
    }
}
