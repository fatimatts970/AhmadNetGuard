package com.ahmad.netguard.network

import com.ahmad.netguard.model.Device

interface RouterAdapter {
    suspend fun login(routerIp: String, username: String, password: String): Boolean
    suspend fun getDevices(): List<Device>
    suspend fun blockDevice(mac: String): Boolean
    suspend fun unblockDevice(mac: String): Boolean
    suspend fun renameDevice(mac: String, newName: String)
    fun brandName(): String
}
