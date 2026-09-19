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

    fun setRememberMe(remember: Boolean) {
        prefs.edit().putBoolean("remember_me", remember).apply()
    }

    fun isRememberMeEnabled(): Boolean = prefs.getBoolean("remember_me", false)

    fun saveGuestWifi(ssid: String, key: String) {
        prefs.edit()
            .putString("guest_ssid", ssid)
            .putString("guest_key", key)
            .apply()
    }

    fun getGuestSsid(): String = prefs.getString("guest_ssid", "") ?: ""
    fun getGuestKey(): String = prefs.getString("guest_key", "") ?: ""

    // Multiple guest profiles saved locally (name;;password, one per line).
    // Router hardware only has ONE guest WLAN slot, so only whichever profile
    // was last "Saved / Turned On" is actually broadcasting — the rest are
    // just remembered here for quick re-use.
    fun getGuestProfiles(): List<Pair<String, String>> {
        val raw = prefs.getString("guest_profiles", "") ?: ""
        if (raw.isBlank()) {
            // Purane version se migrate: agar single guest_ssid saved hai to usay
            // list mein le aao taake purana data gum na ho.
            val oldSsid = getGuestSsid()
            val oldKey = getGuestKey()
            return if (oldSsid.isNotBlank()) listOf(oldSsid to oldKey) else emptyList()
        }
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split(";;")
            if (parts.size == 2) parts[0] to parts[1] else null
        }
    }

    fun saveGuestProfile(ssid: String, key: String, previousName: String? = null) {
        val profiles = getGuestProfiles().toMutableList()
        val matchName = previousName ?: ssid
        val index = profiles.indexOfFirst { it.first == matchName }
        if (index >= 0) {
            profiles[index] = ssid to key
        } else {
            profiles.add(ssid to key)
        }
        persistGuestProfiles(profiles)
        saveGuestWifi(ssid, key)
    }

    fun deleteGuestProfile(ssid: String) {
        val profiles = getGuestProfiles().filterNot { it.first == ssid }
        persistGuestProfiles(profiles)
        if (getGuestSsid() == ssid) saveGuestWifi("", "")
    }

    private fun persistGuestProfiles(profiles: List<Pair<String, String>>) {
        val raw = profiles.joinToString("\n") { "${it.first};;${it.second}" }
        prefs.edit().putString("guest_profiles", raw).apply()
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
    }
}
