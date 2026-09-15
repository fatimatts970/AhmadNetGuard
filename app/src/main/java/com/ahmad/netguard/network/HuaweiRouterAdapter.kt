package com.ahmad.netguard.network

import android.util.Base64
import com.ahmad.netguard.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern

class HuaweiRouterAdapter : RouterAdapter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var gateway: String = "192.168.100.1"
    private var sessionCookie: String = "Cookie=body:Language:english:id=-1"

    override suspend fun login(gateway: String, user: String, pass: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                this@HuaweiRouterAdapter.gateway = gateway
                this@HuaweiRouterAdapter.sessionCookie = "Cookie=body:Language:english:id=-1"

                val tokenRequest = Request.Builder()
                    .url("http://$gateway/asp/GetRandCount.asp")
                    .addHeader("Cookie", sessionCookie)
                    .build()
                val tokenResponse = client.newCall(tokenRequest).execute()
                val token = tokenResponse.body?.string()?.trim() ?: return@withContext false
                if (token.isEmpty()) return@withContext false

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
                    .build()

                val loginResponse = client.newCall(loginRequest).execute()
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
            matcher.appendReplacement(sb, Matcher.quoteReplacement(ch.toString()))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun parseDeviceList(html: String): List<Device> {
        val devices = mutableListOf<Device>()
        val entryPattern = Regex("new USERDevice\\(([^)]+)\\)")

        entryPattern.findAll(html).forEach { match ->
            val inner = match.groupValues[1]
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

    private fun extractToken(html: String): String? =
        Regex("name=\"onttoken\"[^>]*value=\"([0-9a-fA-F]+)\"").find(html)?.groupValues?.get(1)

    private fun findFilterDomain(html: String, mac: String): String? {
        val macNorm = mac.uppercase()
        val pattern = Regex("new stMacFilter\\(\"([^\"]+)\",\"[^\"]*\",\"([^\"]+)\"\\)")
        pattern.findAll(html).forEach { m ->
            val domain = m.groupValues[1]
            val rawMac = unescapeHex(m.groupValues[2]).uppercase()
            if (rawMac == macNorm) return domain
        }
        return null
    }

    override suspend fun blockDevice(mac: String): Boolean = setMacFilter(mac, block = true)

    override suspend fun unblockDevice(mac: String): Boolean = setMacFilter(mac, block = false)

    private suspend fun setMacFilter(mac: String, block: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val pageUrl = "http://$gateway/html/bbsp/wlanmacfilter/wlanmacfilter.asp"
                val pageRequest = Request.Builder()
                    .url(pageUrl)
                    .addHeader("Cookie", sessionCookie)
                    .get()
                    .build()
                val pageHtml = client.newCall(pageRequest).execute().body?.string() ?: return@withContext false
                val token = extractToken(pageHtml) ?: return@withContext false

                if (block) {
                    val addBody = FormBody.Builder()
                        .add("x.SourceMACAddress", mac.uppercase())
                        .add("x.SSIDName", "SSID-1")
                        .add("x.Enable", "1")
                        .add("x.X_HW_Token", token)
                        .build()
                    val addRequest = Request.Builder()
                        .url("http://$gateway/html/bbsp/wlanmacfilter/add.cgi?x=InternetGatewayDevice.X_HW_Security.WLANMacFilter&RequestFile=html/bbsp/wlanmacfilter/wlanmacfilter.asp")
                        .addHeader("Cookie", sessionCookie)
                        .post(addBody)
                        .build()
                    if (!client.newCall(addRequest).execute().isSuccessful) return@withContext false

                    val setBody = FormBody.Builder()
                        .add("x.WlanMacFilterRight", "1")
                        .add("x.X_HW_Token", token)
                        .build()
                    val setRequest = Request.Builder()
                        .url("http://$gateway/html/bbsp/wlanmacfilter/set.cgi?x=InternetGatewayDevice.X_HW_Security&RequestFile=html/bbsp/wlanmacfilter/wlanmacfilter.asp")
                        .addHeader("Cookie", sessionCookie)
                        .post(setBody)
                        .build()
                    client.newCall(setRequest).execute().isSuccessful
                } else {
                    val domain = findFilterDomain(pageHtml, mac) ?: return@withContext true
                    val delBody = FormBody.Builder()
                        .add(domain, "")
                        .add("x.X_HW_Token", token)
                        .build()
                    val delRequest = Request.Builder()
                        .url("http://$gateway/html/bbsp/wlanmacfilter/del.cgi?x=InternetGatewayDevice.X_HW_Security.WLANMacFilter&RequestFile=html/bbsp/wlanmacfilter/wlanmacfilter.asp")
                        .addHeader("Cookie", sessionCookie)
                        .post(delBody)
                        .build()
                    client.newCall(delRequest).execute().isSuccessful
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    override suspend fun getCpuUsagePercent(): Int? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://$gateway/html/ssmp/deviceinfo/deviceinfo.asp")
                    .addHeader("Cookie", sessionCookie)
                    .get()
                    .build()
                val html = client.newCall(request).execute().body?.string() ?: return@withContext null
                Regex("cpuUsed\\s*=\\s*'(\\d+)%'").find(html)?.groupValues?.get(1)?.toIntOrNull()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    override suspend fun getRouterModel(): String? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://$gateway/html/ssmp/deviceinfo/deviceinfo.asp")
                    .addHeader("Cookie", sessionCookie)
                    .get()
                    .build()
                val html = client.newCall(request).execute().body?.string() ?: return@withContext null
                // new stDeviceInfo(domain, SerialNumber, HardwareVersion, SoftwareVersion, ModelName, ...)
                Regex("new stDeviceInfo\\(\"[^\"]*\",\"[^\"]*\",\"[^\"]*\",\"[^\"]*\",\"([^\"]+)\"")
                    .find(html)?.groupValues?.get(1)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    override suspend fun getWifiSsidName(): String? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://$gateway/html/amp/wlaninfo/wlaninfo.asp")
                    .addHeader("Cookie", sessionCookie)
                    .get()
                    .build()
                val html = client.newCall(request).execute().body?.string() ?: return@withContext null
                // First stWlan(...) entry with enable="1" on WLANConfiguration.1 is the main SSID
                val pattern = Regex("new stWlan\\(\"[^\"]*WLANConfiguration\\\\x2e1\",\"1\",\"[^\"]*\",\"([^\"]+)\"")
                pattern.find(html)?.groupValues?.get(1)?.let { unescapeHex(it) }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    override suspend fun isFilterEnabled(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://$gateway/html/bbsp/wlanmacfilter/wlanmacfilter.asp")
                    .addHeader("Cookie", sessionCookie)
                    .get()
                    .build()
                val html = client.newCall(request).execute().body?.string() ?: return@withContext false
                Regex("enableFilter\\s*=\\s*'(\\d)'").find(html)?.groupValues?.get(1) == "1"
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    override suspend fun getBlockedMacs(): Set<String> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://$gateway/html/bbsp/wlanmacfilter/wlanmacfilter.asp")
                    .addHeader("Cookie", sessionCookie)
                    .get()
                    .build()
                val html = client.newCall(request).execute().body?.string() ?: return@withContext emptySet()
                val pattern = Regex("new stMacFilter\\(\"([^\"]+)\",\"[^\"]*\",\"([^\"]+)\"\\)")
                pattern.findAll(html)
                    .map { unescapeHex(it.groupValues[2]).uppercase() }
                    .toSet()
            } catch (e: Exception) {
                e.printStackTrace()
                emptySet()
            }
        }

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
