suspend fun getCurrentWifiName(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://$routerIp/html/network/WlanBasic.asp")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null

                val patterns = listOf(
                    Regex("""SSID\s*[:=]\s*["']([^"']+)["']"""),
                    Regex("""ssid\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                    Regex("""name=["']?ssid["']?[^>]*value=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                    Regex("""id=["']?ssid["']?[^>]*value=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                    Regex("""value=["']([^"']+)["'][^>]*(?:name|id)=["']?ssid["']?""", RegexOption.IGNORE_CASE),
                    Regex("""WLANConfiguration\.1\.SSID\W+["']?([^"'<>,;\s]+)""")
                )
                for (pattern in patterns) {
                    val match = pattern.find(body)
                    if (match != null) return@withContext match.groupValues[1]
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
