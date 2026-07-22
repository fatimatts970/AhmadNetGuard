package com.ahmad.netguard.network

import android.util.Base64
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

    private val deviceRegex = Regex(
        "new USERDevice\\(" +
            "\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\"," +
            "\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\""
    )

    override suspend fun login(routerIp: String, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            routerBaseUrl = "http://$routerIp"
            try {
                val tokenRequest = Request.Builder()
                    .url("$routerBaseUrl/asp/GetRandCount.asp")
                    .post(FormBody.Builder().build())
                    .build()
                val tokenResponse = client.newCall(tokenRequest).execute()
                val token = tokenResponse.body?.string()?.trim() ?: return@withContext false

                val staticCookie = "Cookie=body:Language:english:id=-1"
                val passwordBase64 = Base64.encodeToString(
                    password.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )

                val loginBody = FormBody.Builder()
                    .add("UserName", username)
                    .add("PassWord", passwordBase64)
                    .add("x.X_HW_Token", token)
                    .build()

                val loginRequest = Request.Builder()
                    .url("$routerBaseUrl/login.cgi")
                    .header("Cookie", staticCookie)
                    .post(loginBody)
                    .build()

                val loginResponse = client.newCall(loginRequest).execute()

                val newSessionCookie = loginResponse.headers("Set-Cookie")
                    .joinToString("; ") { it.substringBefore(";") }

                sessionCookie = if (newSessionCookie.isNotBlank()) {
                    "$staticCookie; $newSessionCookie"
                } else {
                    staticCookie
                }

                loginResponse.isSuccessful
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
            val portType = m.groupValues[8]
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

    private fun decodeHexEscapes(raw: String): String {
        val regex = Regex("\\\\x([0-9a-fA-F]{2})")
        return regex.replace(raw) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
    }

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
