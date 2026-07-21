package com.ahmad.netguard.network

import com.ahmad.netguard.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class HuaweiRouterAdapter : RouterAdapter {

    private val client = OkHttpClient()
    private var routerBaseUrl: String = "http://192.168.100.1"
    private var sessionCookie: String? = null

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
                .url("$routerBaseUrl/devicelist.asp")
                .header("Cookie", sessionCookie ?: "")
                .build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext emptyList()
            parseDeviceListHtml(html)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseDeviceListHtml(html: String): List<Device> {
        val doc = Jsoup.parse(html)
        val rows = doc.select("table#deviceList tr.device-row")
        return rows.mapNotNull { row ->
            val mac = row.attr("data-mac").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Device(
                macAddress = mac,
                ipAddress = row.select(".ip").text(),
                routerName = row.select(".hostname").text().ifBlank { "Unknown device" },
                isOnline = row.hasClass("online"),
                isBlocked = row.hasClass("blocked"),
            )
        }
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
