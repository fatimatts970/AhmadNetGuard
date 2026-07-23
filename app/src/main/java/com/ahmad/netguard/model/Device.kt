package com.ahmad.netguard.model

data class Device(
    val name: String,
    val ip: String,
    val mac: String,
    var isBlocked: Boolean = false,
    var isHotspotActive: Boolean = false
)
