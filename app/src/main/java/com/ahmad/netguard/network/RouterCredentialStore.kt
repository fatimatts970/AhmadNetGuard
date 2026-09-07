package com.ahmad.netguard.network

import android.content.Context
import android.content.SharedPreferences

class RouterCredentialStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("router_creds", Context.MODE_PRIVATE)

    fun saveCredentials(gateway: String, user: String, pass: String) {
        prefs.edit()
            .putString("gateway", gateway)
            .putString("username", user)
            .putString("password", pass)
            .apply()
    }

    fun getGateway(): String = prefs.getString("gateway", "192.168.100.1") ?: "192.168.100.1"
    fun getUsername(): String = prefs.getString("username", "admin") ?: "admin"
    fun getPassword(): String = prefs.getString("password", "") ?: ""
    
    fun clearCredentials() {
        prefs.edit().clear().apply()
    }
}
