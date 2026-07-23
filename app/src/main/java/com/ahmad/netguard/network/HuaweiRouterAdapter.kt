package com.ahmad.netguard.network

import com.ahmad.netguard.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class HuaweiRouterAdapter(private var routerIp: String = "192.168.100.1") : RouterAdapter {

    private val sessionCookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                sessionCookieStore[url.host] = cookies.toMutableList()
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return sessionCookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    private var csrfToken: String = ""
    private var username: String = ""
    private var password: String = ""

    // HG8326R real login flow (confirmed from login.asp source):
    //   1. GET  /asp/GetRandCount.asp  -> plain-text anti-replay token
    //   2. POST /login.cgi (form-urlencoded) with UserName, Base64(Password),
    //      and x.X_HW_Token = the token from step 1.
    private suspend fun fetchHwToken(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$routerIp/asp/GetRandCount.asp")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                csrfToken = response.body?.string()?.trim() ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext csrfToken
    }

    override suspend fun login(routerIp: String, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            this@HuaweiRouterAdapter.routerIp = routerIp
            this@HuaweiRouterAdapter.username = username
            this@HuaweiRouterAdapter.password = password
            try {
                val token = fetchHwToken()
                if (token.isEmpty()) return@withContext false

                val encodedPassword = android.util.Base64.encodeToString(
                    password.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )

                val formBody = FormBody.Builder()
                    .add("UserName", username)
                    .add("PassWord", encodedPassword)
                    .add("x.X_HW_Token", token)
                    .build()

                val request = Request.Builder()
                    .url("http://$routerIp/login.cgi")
                    .post(formBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val location = response.header("Location") ?: ""
                    val success = response.code in 300..399 &&
                        !location.contains("login.asp", ignoreCase = true)
                    return@withContext success
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }

    override suspend fun getDevices(): List<Device> = getConnectedDevices()

    override suspend fun renameDevice(mac: String, newName: String) {
        // Huawei firmware does not expose a rename endpoint; names are stored
        // locally via DeviceNameStore and merged in at the UI layer.
    }

    override fun brandName(): String = "Huawei"

    suspend fun getConnectedDevices(): List<Device> = withContext(Dispatchers.IO) {
        val deviceList = mutableListOf<Device>()
        try {
            val request = Request.Builder()
                .url("http://$routerIp/api/monitoring/user-devices")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val xml = response.body?.string() ?: ""

                    val hosts = xml.split("<Host>")
                    for (i in 1 until hosts.size) {
                        val hostXml = hosts[i]
                        val name = hostXml.substringAfter("<HostName>", "Unknown Device").substringBefore("</HostName>")
                        val ip = hostXml.substringAfter("<IPAddress>", "0.0.0.0").substringBefore("</IPAddress>")
                        val mac = hostXml.substringAfter("<MACAddress>", "").substringBefore("</MACAddress>")

                        val isHotspotDetected = checkHotspotSharing(mac)

                        if (mac.isNotEmpty()) {
                            deviceList.add(
                                Device(
                                    macAddress = mac,
                                    displayName = name,
                                    ipAddress = ip,
                                    isOnline = true,
                                    isBlocked = false,
                                    isHotspotActive = isHotspotDetected
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext deviceList
    }

    private fun checkHotspotSharing(mac: String): Boolean {
        return false
    }

    override suspend fun blockDevice(mac: String): Boolean = withContext(Dispatchers.IO) {
        val macAddress = mac
        try {
            val token = if (csrfToken.isEmpty()) fetchHwToken() else csrfToken
            val xmlPayload = """
                <?xml version="1.0" encoding="UTF-8"?>
                <request>
                    <MACFilterControl>1</MACFilterControl>
                    <MACFilterPolicy>1</MACFilterPolicy>
                    <Hosts>
                        <Host>
                            <MAC>$macAddress</MAC>
                        </Host>
                    </Hosts>
                </request>
            """.trimIndent()

            val request = Request.Builder()
                .url("http://$routerIp/api/security/mac-filter")
                .addHeader("__RequestVerificationToken", token)
                .post(xmlPayload.toRequestBody("application/xml".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    override suspend fun unblockDevice(mac: String): Boolean = withContext(Dispatchers.IO) {
        val macAddress = mac
        try {
            val token = if (csrfToken.isEmpty()) fetchHwToken() else csrfToken
            val xmlPayload = """
                <?xml version="1.0" encoding="UTF-8"?>
                <request>
                    <MACFilterControl>0</MACFilterControl>
                    <MACFilterPolicy>1</MACFilterPolicy>
                    <Hosts>
                        <Host>
                            <MAC>$macAddress</MAC>
                        </Host>
                    </Hosts>
                </request>
            """.trimIndent()

            val request = Request.Builder()
                .url("http://$routerIp/api/security/mac-filter")
                .addHeader("__RequestVerificationToken", token)
                .post(xmlPayload.toRequestBody("application/xml".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
