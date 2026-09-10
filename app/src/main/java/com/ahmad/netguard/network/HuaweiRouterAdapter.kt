package com.ahmad.netguard.network

import android.util.Base64
import com.ahmad.netguard.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class HuaweiRouterAdapter : RouterAdapter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var gateway: String = "192.168.100.1"
    private var sessionCookie: String = "Cookie=body:Language:english:id=-1"

    // ==================== LOGIN ====================
    // Verified 10-Sep from a real PCAPdroid capture of the OptiLink app talking
    // to a Huawei HG8326R. Steps:
    // 1. GET /asp/GetRandCount.asp -> body is the plain-text token (no HTML)
    // 2. POST /login.cgi with UserName, base64(PassWord), Language, x.X_HW_Token
    // 3. Response Set-Cookie gives "sid=<token>"; body has
    //    var pageName = 'index.asp'; -> success
    //    var pageName = '/';         -> failure
    override suspend fun login(gateway: String, user: String, pass: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                this@HuaweiRouterAdapter.gateway = gateway
                this@HuaweiRouterAdapter.sessionCookie = "Cookie=body:Language:english:id=-1"

                // Step 1: token
                val tokenRequest = Request.Builder()
                    .url("http://$gateway/asp/GetRandCount.asp")
                    .addHeader("Cookie", sessionCookie)
                    .build()
                val tokenResponse = client.newCall(tokenRequest).execute()
                val token = tokenResponse.body?.string()?.trim() ?: return@withContext false
                if (token.isEmpty()) return@withContext false

                // Step 2: login (password must be base64-encoded)
                val encodedPass = Base64.encodeToString(pass.toByteArray(), Base64.NO_WRAP)
                val formBody = FormBody.Builder()
                    .add("UserName", user)
                    .add("PassWord", encodedPass)
                    .add("Language", "english")
                    .add("x.X_HW_Token", token)
                    .build()

                val loginRequest = Request.Builder()
                    .url("http://$gateway/login.cgi")
                    .addHeader("Cookie", sessionCookie)
                    .addHeader("Referer", "http://$gateway/")
                    .post(formBody)
                    .execute() // placeholder, corrected below
                    .let { null } // unreachable, real call below

                val realLoginRequest = Request.Builder()
                    .url("http://$gateway/login.cgi")
                    .addHeader("Cookie", sessionCookie)
                    .addHeader("Referer", "http://$gateway/")
                    .post(formBody)
                    .build()

                val loginResponse = client.newCall(realLoginRequest).execute()
                val setCookie = loginResponse.headers["Set-Cookie"] ?: ""
                val body = loginResponse.body?.string() ?: ""

                val sidMatch = Regex("sid=([a-fA-F0-9]+)").find(setCookie)
                val pageNameMatch = Regex("pageName\\s*=\\s*'([^']*)'").find(body)
                val pageName = pageNameMatch?.groupValues?.get(1) ?: ""

                val success = sidMatch != null &&
                    pageName.isNotEmpty() &&
                    pageName != "/" &&
                    !pageName.contains("login", ignoreCase = true)

                if (success && sidMatch != null) {
                    sessionCookie = "Cookie=body:Language:english:id=-1; Cookie=sid=${sidMatch.groupValues[1]}:Language:english:id=1"
                }

                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    // ==================== GET CONNECTED DEVICES ====================
    // Verified: response is JS with lines like new USERDevice("Domain","Ip",
    // "Mac","Port","IpType","DevType","DevStatus","PortType","Time","HostName",
    // "IPv4","IPv6","DeviceType"). Special chars come back as \xHH hex escapes.
    override suspend fun getDevices(): List<Device> =
        withContext(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder().build()
                val request = Request.Builder()
                    .url("http://$gateway/html/bbsp/common/GetLanUserDevInfo.asp")
                    .addHeader("Cookie", sessionCookie)
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: return@withContext emptyList()
                parseDeviceList(html)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    private fun unescapeHex(s: String): String {
        val pattern = Pattern.compile("\\\\x([0-9a-fA-F]{2})")
        val matcher = pattern.matcher(s)
        val sb = StringBuffer()
        while (matcher.find()) {
            val hex = matcher.group(1)
            val ch = hex.toInt(16).toChar()
            matcher.appendReplacement(sb, Regex.escape(ch.toString()))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun parseDeviceList(html: String): List<Device> {
        val devices = mutableListOf<Device>()
        val entryPattern = Regex("new USERDevice\\(([^)]+)\\)")

        entryPattern.findAll(html).forEach { match ->
            val inner = match.groupValues[1]
            // split on ',' that separates quoted fields: "a","b","c"
            val fields = Regex("\"([^\"]*)\"").findAll(inner).map { it.groupValues[1] }.toList()
            if (fields.size < 13) return@forEach

            val ipAddr = unescapeHex(fields[1])
            val macAddr = unescapeHex(fields[2])
            val devStatus = fields[6]
            val hostName = unescapeHex(fields[9])

            if (macAddr.isBlank()) return@forEach

            devices.add(
                Device(
                    macAddress = macAddr,
                    displayName = hostName.ifBlank { "Unknown Device" },
                    ipAddress = ipAddr,
                    isOnline = devStatus.equals("Online", ignoreCase = true)
                )
            )
        }
        return devices
    }

    // ==================== BLOCK / UNBLOCK DEVICE ====================
    // Endpoint confirmed from OptiLink's compiled code (strings dump), exact
    // policy/right values not yet verified against a real block action.
    override suspend fun blockDevice(mac: String): Boolean =
        setMacFilter(mac, block = true)

    override suspend fun unblockDevice(mac: String): Boolean =
        setMacFilter(mac, block = false)

    private suspend fun setMacFilter(mac: String, block: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder()
                    .add("x.MACAddress", mac)
                    .add("x.WlanMacFilterPolicy", "0")
                    .add("x.WlanMacFilterRight", if (block) "0" else "1")
                    .build()

                val request = Request.Builder()
                    .url("http://$gateway/html/bbsp/wlanmacfilter/add.cgi?x=InternetGatewayDevice.X_HW_Security.WLANMacFilter")
                    .addHeader("Cookie", sessionCookie)
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
    // Endpoint confirmed from OptiLink's compiled code.
    override suspend fun restartRouter(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://$gateway/set.cgi?x=InternetGatewayDevice.X_HW_DEBUG.SSP.DBSave&y=InternetGatewayDevice.X_HW_DEBUG.SMP.DM.ResetBoard&RequestFile=")
                    .addHeader("Cookie", sessionCookie)
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
    // Endpoint + field names confirmed from OptiLink's compiled code, exact
    // save flow not yet verified against a real save action.
    override suspend fun updateWifiSettings(ssid: String, key: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder()
                    .add("w.SSID", ssid)
                    .add("w.Key", key)
                    .add("k.PreSharedKey", key)
                    .add("RequestFile", "html/amp/wlanbasic/WlanBasic.asp")
                    .build()

                val request = Request.Builder()
                    .url("http://$gateway/html/amp/wlanbasic/set.cgi")
                    .addHeader("Cookie", sessionCookie)
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
