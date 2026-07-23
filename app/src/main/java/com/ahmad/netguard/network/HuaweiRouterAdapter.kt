package com.ahmad.netguard.network

import com.ahmad.netguard.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class HuaweiRouterAdapter(private var routerIp: String = "192.168.100.1") : RouterAdapter {

    private val sessionCookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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

    private fun hashPassword(plain: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(plain.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return android.util.Base64.encodeToString(hex.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    }

    override suspend fun login(routerIp: String, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            this@HuaweiRouterAdapter.routerIp = routerIp
            this@HuaweiRouterAdapter.username = username
            this@HuaweiRouterAdapter.password = password
            try {
                val token = fetchCsrfToken()
                if (token.isEmpty()) return@withContext false

                val loginPayload = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <request>
                        <Username>$username</Username>
                        <Password>${hashPassword(password)}</Password>
                        <password_type>4</password_type>
                    </request>
                """.trimIndent()

                val request = Request.Builder()
                    .url("http://$routerIp/api/user/login")
                    .addHeader("__RequestVerificationToken", token)
                    .post(loginPayload.toRequestBody("application/xml".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    return@withContext response.isSuccessful && !body.contains("<error>")
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

    suspend fun fetchCsrfToken(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$routerIp/api/webserver/SesTokInfo")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                csrfToken = when {
                    body.contains("<TokInfo>") ->
                        body.substringAfter("<TokInfo>").substringBefore("</TokInfo>")
                    body.contains("<token>") ->
                        body.substringAfter("<token>").substringBefore("</token>")
                    else -> csrfToken
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext csrfToken
    }

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
            val token = if (csrfToken.isEmpty()) fetchCsrfToken() else csrfToken
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
            val token = if (csrfToken.isEmpty()) fetchCsrfToken() else csrfToken
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
