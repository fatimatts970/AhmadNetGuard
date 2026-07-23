package com.ahmad.netguard.model

data class Device(
    val macAddress: String,
    val displayName: String,
    val ipAddress: String,
    val isOnline: Boolean = true,
    var isBlocked: Boolean = false,
    var isHotspotActive: Boolean = false
) {
    val name: String get() = displayName
    val mac: String get() = macAddress
    val ip: String get() = ipAddress
}
