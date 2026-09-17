package com.ahmad.netguard.network

import com.ahmad.netguard.model.Device
import com.ahmad.netguard.model.OpticalInfo

interface RouterAdapter {
    suspend fun login(routerIp: String, username: String, password: String): Boolean
    suspend fun getDevices(): List<Device>
    suspend fun blockDevice(mac: String): Boolean
    suspend fun unblockDevice(mac: String): Boolean
    suspend fun getBlockedMacs(): Set<String>
    suspend fun isFilterEnabled(): Boolean
    suspend fun getCpuUsagePercent(): Int?
    suspend fun getRouterModel(): String?
    suspend fun setGuestWifi(ssid: String, key: String, enable: Boolean): Boolean
    suspend fun setGuestWifiDiagnostic(ssid: String, key: String, enable: Boolean): String
    suspend fun getOpticalInfo(): OpticalInfo?
    suspend fun getWifiSsidName(): String?
    suspend fun restartRouter(): Boolean
    suspend fun updateWifiSettings(ssid: String, key: String): Boolean
}
