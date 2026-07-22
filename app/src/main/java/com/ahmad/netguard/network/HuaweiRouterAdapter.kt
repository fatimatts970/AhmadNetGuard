package com.ahmad.netguard.network

import com.ahmad.netguard.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class HuaweiRouterAdapter : RouterAdapter {

    private val client = OkHttpClient()
    private var routerBaseUrl: String = "http://192.168.100.1"
    private var sessionCookie: String? = null

    // Matches: new USERDevice("Domain","IpAddr","MacAddr","Port","IpType","DevType","DevStatus","PortType","Time","HostName", ...)
    private val deviceRegex = Regex(
        "new USERDevice\\(" +
            "\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\"," +
            "\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\""
    )

    // NOTE: login() is still a placeholder — this router's real login uses a
    // token/hashed-password challenge, not a plain username+password POST.
    // This needs to be captured separately from the browser's Network tab
    // during an actual login (see the login.cgi / token request).
    override suspend fun login(routerIp: String, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            routerBaseUrl = "http://$routerIp"
            try {
                val body = FormBody.Builder()
                    .add("UserName", username)
                    .add("PassWord", password)
                    .build()
                val request = Request.Builder()
                    .url("$routerBaseUrl/login.cgi")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                sessionCookie = response.header("Set-Cookie")
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }

    override suspend fun getDevices(): List<Device> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$routerBaseUrl/html/bbsp/common/GetLanUserDevInfo.asp")
                .header("Cookie", sessionCookie ?: "")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            parseDeviceListJs(body)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseDeviceListJs(js: String): List<Device> {
        return deviceRegex.findAll(js).mapNotNull { m ->
            val ipAddr = decodeHexEscapes(m.groupValues[2])
            val macAddr = decodeHexEscapes(m.groupValues[3])
            val portType = m.groupValues[8] // WIFI / LAN
            val time = decodeHexEscapes(m.groupValues[9])
            val devStatus = m.groupValues[7]
            val hostName = decodeHexEscapes(m.groupValues[10])

            if (macAddr.isBlank()) return@mapNotNull null

            Device(
                macAddress = macAddr,
                ipAddress = ipAddr,
                routerName = hostName.ifBlank { "Unknown device" },
                isOnline = devStatus.equals("Online", ignoreCase = true),
                connectionType = if (portType.equals("WIFI", ignoreCase = true)) "WiFi" else "LAN",
                connectedSinceMinutes = parseHoursColonMinutes(time),
            )
        }.toList()
    }

    // Router encodes special characters as \xHH inside the JS string literals
    // (e.g. "192\x2e168\x2e100\x2e4" -> "192.168.100.4")
    private fun decodeHexEscapes(raw: String): String {
        val regex = Regex("\\\\x([0-9a-fA-F]{2})")
        return regex.replace(raw) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
    }

    // Time field looks like "2:33" meaning 2 hours 33 minutes connected
    private fun parseHoursColonMinutes(time: String): Int? {
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        return hours * 60 + minutes
    }

    override suspend fun blockDevice(mac: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder().add("mac", mac).add("action", "block").build()
            val request = Request.Builder()
                .url("$routerBaseUrl/macfilter.cgi")
                .header("Cookie", sessionCookie ?: "")
                .post(body)
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun unblockDevice(mac: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder().add("mac", mac).add("action", "unblock").build()
            val request = Request.Builder()
                .url("$routerBaseUrl/macfilter.cgi")
                .header("Cookie", sessionCookie ?: "")
                .post(body)
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun renameDevice(mac: String, newName: String) {
    }

    override fun brandName(): String = "Huawei"
}
