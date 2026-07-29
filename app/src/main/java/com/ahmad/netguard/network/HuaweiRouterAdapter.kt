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

    private var csrfToken: String = ""
    private var username: String = ""
    private var password: String = ""

    private suspend fun fetchHwToken(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$routerIp/asp/GetRandCount.asp")
                .post(FormBody.Builder().build())
                .build()

            client.newCall(request).execute().use { response ->
                csrfToken = response.body?.string()?.trim() ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext csrfToken
    }

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

    override suspend fun login(routerIp: String, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            this@HuaweiRouterAdapter.routerIp = routerIp
            this@HuaweiRouterAdapter.username = username
            this@HuaweiRouterAdapter.password = password
            try {
                val initRequest = Request.Builder()
                    .url("http://$routerIp/login.asp")
                    .header("User-Agent", browserUserAgent)
                    .get()
                    .build()
                client.newCall(initRequest).execute().close()

                val token = fetchHwToken()
                if (token.isEmpty()) return@withContext false

                injectPreLoginCookie(routerIp)

                val encodedPassword = android.util.Base64.encodeToString(
                    password.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )

                val formBody = FormBody.Builder()
                    .add("UserName", username)
                    .add("PassWord", encodedPassword)
                    .add("x.X_HW_Token", token)
                    .build()

                val loginRequest = Request.Builder()
                    .url("http://$routerIp/login.cgi")
                    .header("User-Agent", browserUserAgent)
                    .header("Referer", "http://$routerIp/login.asp")
                    .header("Origin", "http://$routerIp")
                    .post(formBody)
                    .build()

                client.newCall(loginRequest).execute().close()

                val checkRequest = Request.Builder()
                    .url("http://$routerIp/index.asp")
                    .header("User-Agent", browserUserAgent)
                    .get()
                    .build()

                client.newCall(checkRequest).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    val body = response.body?.string() ?: ""
                    val bouncedToLogin = finalUrl.contains("login.asp", ignoreCase = true) ||
                        body.contains("login.asp", ignoreCase = true)
                    return@withContext response.isSuccessful && !bouncedToLogin
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }

    suspend fun isSessionAlive(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$routerIp/html/ssmp/common/refreshTime.asp")
                .header("User-Agent", browserUserAgent)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                (response.body?.string()?.trim() ?: "") == "1"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun getDevices(): List<Device> = getConnectedDevices()

    override suspend fun renameDevice(mac: String, newName: String) {
    }

    override fun brandName(): String = "Huawei"

    suspend fun getConnectedDevices(): List<Device> = withContext(Dispatchers.IO) {
        val deviceList = mutableListOf<Device>()
        try {
            val request = Request.Builder()
                .url("http://$routerIp/html/bbsp/common/GetLanUserDevInfo.asp")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val js = response.body?.string() ?: ""

                val macRegex = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
                val ipRegex = Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b")
                val entryRegex = Regex("new USERDevice\\(([^)]*)\\)")

                for (match in entryRegex.findAll(js)) {
                    val argsRaw = match.groupValues[1]
                    val args = argsRaw.split(",").map { it.trim().trim('"', '\'') }

                    val mac = args.firstOrNull { macRegex.matches(it) } ?: continue
                    val ip = args.firstOrNull { ipRegex.matches(it) } ?: "0.0.0.0"
                    val name = args.firstOrNull {
                        it.isNotBlank() && it != mac && it != ip && !it.matches(Regex("^[01]$"))
                    } ?: "Unknown Device"

                    deviceList.add(
                        Device(
                            macAddress = mac,
                            displayName = name,
                            ipAddress = ip,
                            isOnline = true,
                            isBlocked = false,
                            isHotspotActive = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext deviceList
    }

    private suspend fun ensureBlacklistModeEnabled(token: String) {
        try {
            val formBody = FormBody.Builder()
                .add("x.MacFilterRight", "1")
                .add("x.MacFilterPolicy", "1")
                .add("x.X_HW_Token", token)
                .build()

            val request = Request.Builder()
                .url("http://$routerIp/set.cgi?x=InternetGatewayDevice.X_HW_Security&RequestFile=html/bbsp/macfilter/macfilter.asp")
                .post(formBody)
                .build()

            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchWifiPageToken(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$routerIp/html/amp/wlanbasic/WlanBasic.asp")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext ""
                val match = Regex("""hwonttoken\s*[:=]\s*["']?([a-fA-F0-9]+)["']?""").find(body)
                match?.groupValues?.get(1) ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun getCurrentWifiName(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$routerIp/html/amp/wlanbasic/WlanBasic.asp")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null

                val patterns = listOf(
                    Regex("""w\.SSID\W+["']?([^"'<>,;\s]+)"""),
                    Regex("""SSID\s*[:=]\s*["']([^"']+)["']"""),
                    Regex("""name=["']?ssid["']?[^>]*value=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                )
                for (pattern in patterns) {
                    val match = pattern.find(body)
                    val candidate = match?.groupValues?.get(1)
                    val looksLikeRealName = candidate != null &&
                        candidate.isNotBlank() &&
                        !candidate.contains("(") &&
                        !candidate.contains(")")
                    if (looksLikeRealName) {
                        return@withContext candidate
                    }
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun changeWifiSettings(ssid: String, password: String, hideSsid: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = fetchWifiPageToken()
            val wlanDomain = "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1"

            val formBody = FormBody.Builder()
                .add("w.SSID", ssid)
                .add("k.PreSharedKey", password)
                .add("w.SSIDAdvertisementEnabled", if (hideSsid) "0" else "1")
                .add("w.BeaconType", "WPAand11i")
                .add("w.BasicAuthenticationMode", "PSKAuthentication")
                .add("w.BasicEncryptionModes", "TKIPandAESEncryption")
                .add("w.WPAAuthenticationMode", "PSKAuthentication")
                .add("w.WPAEncryptionModes", "TKIPandAESEncryption")
                .add("w.IEEE11iAuthenticationMode", "PSKAuthentication")
                .add("w.IEEE11iEncryptionModes", "TKIPandAESEncryption")
                .add("w.X_HW_WPAand11iAuthenticationMode", "PSKAuthentication")
                .add("w.X_HW_WPAand11iEncryptionModes", "TKIPandAESEncryption")
                .add("w.X_HW_GroupRekey", "3600")
                .add("hwonttoken", token)
                .build()

            val request = Request.Builder()
                .url("http://$routerIp/html/amp/wlanbasic/set.cgi?y=$wlanDomain&z=$wlanDomain.WPS&k=$wlanDomain.PreSharedKey.1&RequestFile=html/amp/wlanbasic/WlanBasic.asp")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun rebootRouter(): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = if (csrfToken.isEmpty()) fetchHwToken() else csrfToken

            val formBody = FormBody.Builder()
                .add("x.X_HW_Token", token)
                .build()

            val request = Request.Builder()
                .url("http://$routerIp/html/ssmp/accoutcfg/set.cgi?x=InternetGatewayDevice.X_HW_DEBUG.SMP.DM.ResetBoard&RequestFile=html/ssmp/accoutcfg/ontmngt.asp")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    override suspend fun blockDevice(mac: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = if (csrfToken.isEmpty()) fetchHwToken() else csrfToken
            ensureBlacklistModeEnabled(token)

            val formBody = FormBody.Builder()
                .add("x.SourceMACAddress", mac)
                .add("x.X_HW_Token", token)
                .build()

            val request = Request.Builder()
                .url("http://$routerIp/add.cgi?x=InternetGatewayDevice.X_HW_Security.MacFilter&RequestFile=html/bbsp/macfilter/macfilter.asp")
                .post(formBody)
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
        try {
            val token = if (csrfToken.isEmpty()) fetchHwToken() else csrfToken

            val formBody = FormBody.Builder()
                .add("x.SourceMACAddress", mac)
                .add("x.X_HW_Token", token)
                .build()

            val request = Request.Builder()
                .url("http://$routerIp/del.cgi?x=InternetGatewayDevice.X_HW_Security.MacFilter&RequestFile=html/bbsp/macfilter/macfilter.asp")
                .post(formBody)
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
