package com.ahmad.netguard.network

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Huawei router ke .asp pages read karne ka generic tareeqa (sirf padhna, koi change nahi).
 * Page ke andar "new stXxx("a","b",...)" calls aur getElementById/var fields nikalta hai.
 */
object PageReader {

    class Call(val name: String, val args: List<String>)

    class Scraped(val calls: List<Call>, val fields: LinkedHashMap<String, String>)

    class Result(val path: String, val html: String, val scraped: Scraped)

    private val skipCalls = setOf(
        "Array", "Date", "Object", "Image", "XMLHttpRequest", "RegExp", "Function", "Error",
        "Option", "String", "Number", "Boolean"
    )

    private val callRegex = Regex("new\\s+(\\w+)\\s*\\(([^)]*)\\)")
    private val argRegex = Regex("\\x22((?:[^\\x22\\\\]|\\\\.)*)\\x22|\\x27((?:[^\\x27\\\\]|\\\\.)*)\\x27|([^,\\s\\x22\\x27]+)")
    private val byIdRegex = Regex(
        "getElementById\\(\\s*[\\x22\\x27]([\\w.\\-]+)[\\x22\\x27]\\s*\\)\\s*\\.\\s*(?:innerHTML|innerText|value|textContent)\\s*=\\s*[\\x22\\x27]([^\\x22\\x27]*)[\\x22\\x27]"
    )
    private val varRegex = Regex("var\\s+(\\w+)\\s*=\\s*[\\x22\\x27]([^\\x22\\x27]{0,200})[\\x22\\x27]\\s*;")
    private val idCellRegex = Regex("id=\\x22([\\w.\\-]+)\\x22[^>]*>\\s*([^<]{1,120}?)\\s*<")
    private val secretKey = Regex("(?i)token|passw|pwd|key|secret")

    fun unescapeHex(s: String): String {
        val matcher: Matcher = Pattern.compile("\\\\x([0-9a-fA-F]{2})").matcher(s)
        val sb = StringBuffer()
        while (matcher.find()) {
            val ch = matcher.group(1).toInt(16).toChar()
            matcher.appendReplacement(sb, Matcher.quoteReplacement(ch.toString()))
        }
        matcher.appendTail(sb)
        return com.ahmad.netguard.ui.TextFix.decode(sb.toString())
    }

    private fun looksValid(body: String): Boolean =
        body.length > 80 &&
            !body.contains("404 Not Found", ignoreCase = true) &&
            !body.contains("Frm_Username")

    /** Pehla aisa path jo valid page de. */
    suspend fun fetchFirst(paths: List<String>): Result? {
        val ad = RouterAdapterFactory.getAdapter() as? HuaweiRouterAdapter ?: return null
        var sawLogin = false
        for (attempt in 0..1) {
            for (p in paths) {
                val body = ad.fetchPage(p) ?: continue
                if (body.contains("Frm_Username")) sawLogin = true
                if (looksValid(body)) return Result(p, body, scrape(body))
            }
            // sirf tab dobara login karo jab router ne login page diya ho (404 par nahi)
            if (attempt == 0 && (!sawLogin || !com.ahmad.netguard.ui.SessionKeeper.relogin())) return null
        }
        return null
    }

    fun scrape(html: String): Scraped {
        val calls = ArrayList<Call>()
        val startRe = Regex("new\\s+(\\w+)\\s*\\(")
        for (m in startRe.findAll(html)) {
            val name = m.groupValues[1]
            if (name in skipCalls) continue
            var i = m.range.last + 1
            var depth = 1
            var quote: Char? = null
            val sb = StringBuilder()
            while (i < html.length && depth > 0 && sb.length < 4000) {
                val ch = html[i]
                val q = quote
                if (q != null) {
                    sb.append(ch)
                    if (ch == '\\' && i + 1 < html.length) {
                        sb.append(html[i + 1])
                        i++
                    } else if (ch == q) {
                        quote = null
                    }
                } else if (ch == '"' || ch == '\'') {
                    quote = ch
                    sb.append(ch)
                } else if (ch == '(') {
                    depth++
                    sb.append(ch)
                } else if (ch == ')') {
                    depth--
                    if (depth > 0) sb.append(ch)
                } else {
                    sb.append(ch)
                }
                i++
            }
            val args = argRegex.findAll(sb.toString()).map { a ->
                val raw = when {
                    a.groups[1] != null -> a.groups[1]!!.value
                    a.groups[2] != null -> a.groups[2]!!.value
                    else -> a.groups[3]!!.value
                }
                unescapeHex(raw).trim()
            }.toList()
            if (args.isNotEmpty()) calls.add(Call(name, args))
        }

        val fields = LinkedHashMap<String, String>()
        fun put(k: String, v: String) {
            val value = unescapeHex(v).trim()
            if (value.isEmpty() || secretKey.containsMatchIn(k)) return
            if (fields.size < 80 && !fields.containsKey(k)) fields[k] = value
        }
        for (m in byIdRegex.findAll(html)) put(m.groupValues[1], m.groupValues[2])
        for (m in varRegex.findAll(html)) put(m.groupValues[1], m.groupValues[2])
        for (m in idCellRegex.findAll(html)) put(m.groupValues[1], m.groupValues[2])
        return Scraped(calls, fields)
    }

    /** Copy/share ke liye: lambay hex tokens chhupa deta hai. */
    fun sanitize(raw: String): String =
        raw.replace(Regex("[0-9a-fA-F]{24,}"), "****")
}
