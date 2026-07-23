package com.ahmad.netguard.network

import com.ahmad.netguard.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class HuaweiRouterAdapter(private val routerIp: String = "192.168.100.1") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var csrfToken: String = ""

    suspend fun fetchCsrfToken(): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$routerIp/api/webserver/token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (body.contains("<token>")) {
                    csrfToken = body.substringAfter("<token>").substringBefore("</token>")
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
                                    name = name,
                                    ip = ip,
                                    mac = mac,
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

    suspend fun blockDevice(macAddress: String): Boolean = withContext(Dispatchers.IO) {
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

    suspend fun unblockDevice(macAddress: String): Boolean = withContext(Dispatchers.IO) {
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
