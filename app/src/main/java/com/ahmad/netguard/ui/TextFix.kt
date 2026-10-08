package com.ahmad.netguard.ui

/** Router naam aksar "\u2620\ufe0f" ya "\x41" jaise escapes mein deta hai; unhe asli text bana deta hai. */
object TextFix {

    private val hexEsc = Regex("\\\\x([0-9a-fA-F]{2})")
    private val uniEsc = Regex("\\\\u([0-9a-fA-F]{4})")

    fun decode(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        var out: String = s
        out = hexEsc.replace(out) { m -> m.groupValues[1].toInt(16).toChar().toString() }
        out = uniEsc.replace(out) { m -> m.groupValues[1].toInt(16).toChar().toString() }
        return out
    }

    /** Sirf English letters/digits (lowercase): naam ka asli hissa compare karne ke liye, emoji ignore. */
    fun plain(s: String): String {
        val sb = StringBuilder()
        for (ch in s) if (ch.code < 128 && ch.isLetterOrDigit()) sb.append(ch.lowercaseChar())
        return sb.toString()
    }
}
