package com.ahmad.netguard.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class RouterCredentialStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "router_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hasSavedCredentials(): Boolean =
        prefs.contains("router_ip") && prefs.contains("username") && prefs.contains("password")

    fun save(routerIp: String, username: String, password: String) {
        prefs.edit()
            .putString("router_ip", routerIp)
            .putString("username", username)
            .putString("password", password)
            .apply()
    }

    fun getRouterIp(): String = prefs.getString("router_ip", "192.168.100.1") ?: "192.168.100.1"
    fun getUsername(): String = prefs.getString("username", "") ?: ""
    fun getPassword(): String = prefs.getString("password", "") ?: ""

    fun clear() {
        prefs.edit().clear().apply()
    }
}
