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
