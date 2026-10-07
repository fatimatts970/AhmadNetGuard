package com.ahmad.netguard.ui

/** Aakhri jaani hui router values: screens turant dikha sakein, phir fresh data se update ho. */
object RouterCache {
    @Volatile var model: String? = null
    @Volatile var ssid: String? = null
    @Volatile var cpu: Int? = null
    @Volatile var online: Int? = null
    @Volatile var known: Int? = null
    @Volatile var blocked: Int? = null
}
