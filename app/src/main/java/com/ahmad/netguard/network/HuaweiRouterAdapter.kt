package com.ahmad.netguard.network

import com.ahmad.netguard.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class HuaweiRouterAdapter : RouterAdapter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val session = RouterSession()

    // Gateway default
    private var gateway: String = "192.168.100.1"

    // ==================== LOGIN ====================
    override suspend fun login(gateway: String, user: String, pass: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                this@HuaweiRouterAdapter.gateway = gateway

                // Step 1: Get Token
                val tokenRequest = Request.Builder()
                    .url("http://$gateway/asp/GetRandCount.asp")
                    .build()

                val tokenResponse = client.newCall(tokenRequest).execute()
                val tokenBody = tokenResponse.body?.string() ?: ""

                val token = tokenBody.substringAfter(
                    "<input type=\"hidden\" name=\"x.X_HW_Token\" value=\""
                ).substringBefore("\"")

                // Step 2: Login POST
                val formBody = FormBody.Builder()
                    .add("UserName", user)
                    .add("PassWord", pass)
                    .add("x.X_HW_Token", token)
                    .build()

                val loginRequest = Request.Builder()
                    .url("http://$gateway/login.cgi")
                    .addHeader("Cookie", "body:Language:english:id=-1")
                    .post(formBody)
                    .build()

                val loginResponse = client.newCall(loginRequest).execute()
                val responseBody = loginResponse.body?.string() ?: ""

                val success = responseBody.contains("top.location.href") ||
                        responseBody.contains("parent.location") ||
                        loginResponse.code == 302

                // Extract session cookie
                val setCookie = loginResponse.headers["Set-Cookie"] ?: ""
                val sessionId = setCookie.substringBefore(";")

                session.isLoggedIn = success
                session.token = token
                session.sessionInfo = sessionId
                session.gateway = gateway

                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    // ==================== GET CONNECTED DEVICES ====================
    override suspend fun getConnectedDevices(): List<Device> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://${session.gateway}/html/status/GetLanUserDevInfo.asp")
                    .addHeader("Cookie", session.sessionInfo ?: "")
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: return@withContext emptyList()

                parseDeviceHtml(html)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    private fun parseDeviceHtml(html: String): List<Device> {
        val devices = mutableListOf<Device>()
        val pattern = Regex(
            "<tr>.*?<td>(.*?)</td>.*?<td>(.*?)</td>.*?<td>(.*?)</td>.*?</tr>",
            RegexOption.DOT_MATCHES_ALL
        )

        pattern.findAll(html).forEach { match ->
            val ip = match.groupValues[1].trim()
            val mac = match.groupValues[2].trim()
            val name = match.groupValues[3].trim()
            if (mac.isNotEmpty() && ip.isNotEmpty()) {
                devices.add(Device(mac, ip, name))
            }
        }
        return devices
    }

    // ==================== BLOCK DEVICE ====================
    override suspend fun blockDevice(mac: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder()
                    .add("mac", mac)
                    .add("x.WlanMacFilterPolicy", "0")
                    .add("x.WlanMacFilterRight", "0")
                    .build()

                val request = Request.Builder()
                    .url("http://${session.gateway}/html/bbsp/wlanmacfilter/add.cgi")
                    .addHeader("Cookie", session.sessionInfo ?: "")
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    // ==================== UNBLOCK DEVICE ====================
    override suspend fun unblockDevice(mac: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder()
                    .add("mac", mac)
                    .build()

                val request = Request.Builder()
                    .url("http://${session.gateway}/html/bbsp/wlanmacfilter/del.cgi")
                    .addHeader("Cookie", session.sessionInfo ?: "")
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    // ==================== RESTART ROUTER ====================
    override suspend fun restartRouter(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://${session.gateway}/html/ssmp/reboot/set.cgi")
                    .addHeader("Cookie", session.sessionInfo ?: "")
                    .post(FormBody.Builder().build())
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    // ==================== UPDATE WIFI SETTINGS ====================
    override suspend fun updateWifiSettings(ssid: String, key: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder()
                    .add("ssid", ssid)
                    .add("key", key)
                    .add("RequestFile", "html/amp/wlanbasic/WlanBasic.asp")
                    .build()

                val request = Request.Builder()
                    .url("http://${session.gateway}/html/amp/wlanbasic/set.cgi")
                    .addHeader("Cookie", session.sessionInfo ?: "")
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
}
