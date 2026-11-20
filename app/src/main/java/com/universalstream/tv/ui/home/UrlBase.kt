package com.universalstream.tv.ui.home

import java.util.Locale

/**
 * The base that the value typed in the URL box is combined with before it is opened.
 *
 * [Manual] keeps the original behaviour: the whole address is typed (or pasted) by hand.
 * The shortener bases let the viewer type only the short code (the part after the "/") and have
 * the matching short-link host added automatically, which is far less typing on a TV remote.
 */
enum class UrlBase(val label: String, val prefix: String?) {
    Manual("Manual (full URL)", null),
    ShortUrl("shorturl.at/", "https://shorturl.at/"),
    Bitly("bit.ly/", "https://bit.ly/"),
    TinyUrl("tinyurl.com/", "https://tinyurl.com/");

    /** The bare host of the base, e.g. "shorturl.at" (null for [Manual]). */
    val host: String? get() = prefix?.removePrefix("https://")?.trimEnd('/')

    /**
     * Combines what the viewer typed with this base into a value that [com.universalstream.tv.core.UrlUtils.parse]
     * can understand. It is forgiving: a full address or one that already includes the base host is left alone,
     * so pasting a complete link never produces a doubled-up URL.
     */
    fun combine(typed: String): String {
        val code = typed.trim()
        val base = prefix ?: return code
        if (code.isEmpty()) return code
        val lower = code.lowercase(Locale.ROOT)
        // Already a full address: leave it exactly as typed/pasted.
        if (lower.startsWith("http://") || lower.startsWith("https://")) return code
        // Viewer already typed the base host ("shorturl.at/abc"): just add the scheme.
        host?.let { h ->
            if (lower == h || lower.startsWith("$h/")) return "https://$code"
        }
        return base + code.trimStart('/')
    }
}
