package com.ahmad.netguard.network

import com.ahmad.netguard.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class HuaweiRouterAdapter : RouterAdapter {

    private val sessionCookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val browserUserAgent =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                sessionCookieStore[url.host] = cookies.toMutableList()
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return sessionCookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    private val session = RouterSession()

    private var gateway: String = "192.168.100.1"

    private fun injectPreLoginCookie(routerIp: String) {
        val cookie = Cookie.Builder()
            .name("Cookie")
            .value("body:Language:english:id=-1")
            .domain(routerIp)
            .path("/")
            .build()
        val existing = sessionCookieStore.getOrPut(routerIp) { mutableListOf() }
        existing.removeAll { it.name == "Cookie" }
        existing.add(cookie)
    }

    override suspend fun login(gateway: String, user: String, pass: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                this@HuaweiRouterAdapter.gateway = gateway
                session.gateway = gateway

                val initRequest = Request.Builder()
                    .url("http://$gateway/login.asp")
                    .header("User-Agent", browserUserAgent)
                    .get()
                    .build()
                client.newCall(initRequest).execute().close()

                val tokenRequest = Request.Builder()
                    .url("http://$gateway/asp/GetRandCount.asp")
                    .header("User-Agent", browserUserAgent)
                    .post(FormBody.Builder().build())
                    .build()

                val token = client.newCall(tokenRequest).execute().use { response ->
                    response.body?.string()?.trim() ?: ""
                }
                if (token.isEmpty()) return@withContext false

                injectPreLoginCookie(gateway)

                val encodedPassword = android.util.Base64.encodeToString(
                    pass.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )

                val formBody = FormBody.Builder()
                    .add("UserName", user)
                    .add("PassWord", encodedPassword)
                    .add("x.X_HW_Token", token)
                    .build()

                val loginRequest = Request.Builder()
                    .url("http://$gateway/login.cgi")
                    .header("User-Agent", browserUserAgent)
                    .header("Referer", "http://$gateway/login.asp")
                    .header("Origin", "http://$gateway")
                    .post(formBody)
                    .build()

                client.newCall(loginRequest).execute().close()

                val checkRequest = Request.Builder()
                    .url("http://$gateway/index.asp")
                    .header("User-Agent", browserUserAgent)
                    .get()
                    .build()

                val success = client.newCall(checkRequest).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    val body = response.body?.string() ?: ""
                    val bouncedToLogin = finalUrl.contains("login.asp", ignoreCase = true) ||
                        body.contains("login.asp", ignoreCase = true)
                    response.isSuccessful && !bouncedToLogin
                }

                session.isLoggedIn = success
                session.token = token
                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    override suspend fun getDevices(): List<Device> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://${session.gateway}/html/status/GetLanUserDevInfo.asp")
                    .header("User-Agent", browserUserAgent)
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
                devices.add(Device(macAddress = mac, displayName = name, ipAddress = ip))
            }
        }
        return devices
    }

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
                    .header("User-Agent", browserUserAgent)
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    override suspend fun unblockDevice(mac: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder()
                    .add("mac", mac)
                    .build()

                val request = Request.Builder()
                    .url("http://${session.gateway}/html/bbsp/wlanmacfilter/del.cgi")
                    .header("User-Agent", browserUserAgent)
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    override suspend fun restartRouter(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://${session.gateway}/html/ssmp/reboot/set.cgi")
                    .header("User-Agent", browserUserAgent)
                    .post(FormBody.Builder().build())
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

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
                    .header("User-Agent", browserUserAgent)
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
