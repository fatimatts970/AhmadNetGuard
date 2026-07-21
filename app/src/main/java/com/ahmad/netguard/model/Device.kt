package com.ahmad.netguard.model

data class Device(
    val macAddress: String,
    val ipAddress: String,
    var routerName: String,
    var customName: String? = null,
    var isOnline: Boolean = false,
    var isBlocked: Boolean = false,
    var connectionType: String = "WiFi",
    var signalDbm: Int? = null,
    var downloadBytesTotal: Long = 0L,
    var uploadBytesTotal: Long = 0L,
    var downloadSpeedBps: Long = 0L,
    var uploadSpeedBps: Long = 0L,
    var connectedSinceMinutes: Int? = null,
    var possibleHotspotShare: Boolean = false,
) {
    fun displayName(): String = customName?.takeIf { it.isNotBlank() } ?: routerName
}
