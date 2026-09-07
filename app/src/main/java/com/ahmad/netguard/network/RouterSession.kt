package com.ahmad.netguard.network

data class RouterSession(
    var token: String? = null,
    var sessionInfo: String? = null,
    var isLoggedIn: Boolean = false,
    var gateway: String = "192.168.100.1"
)
