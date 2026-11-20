package com.universalstream.tv.resolver

enum class HopKind { HTTP_REDIRECT, META_REFRESH, JS_REDIRECT, BROWSER_NAVIGATION }

data class ClientRedirect(val kind: HopKind, val target: String)

/**
 * Finds client-side redirects in the first part of an HTML page:
 * `<meta http-equiv="refresh" content="0;url=…">` and simple `location = "…"` /
 * `location.replace("…")` scripts. Anything more dynamic is left to the WebView fallback.
 * The returned target may be relative; the caller resolves it against the page URL.
 */
object HtmlRedirectParser {
    private val OPTS = setOf(RegexOption.IGNORE_CASE)
    private val META_TAG = Regex("<meta\\b[^>]*>", OPTS)
    private val HTTP_EQUIV_REFRESH = Regex("http-equiv\\s*=\\s*[\"']?\\s*refresh", OPTS)
    private val CONTENT_ATTR = Regex("\\bcontent\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", OPTS)
    private val REFRESH_URL = Regex("^\\s*\\d*(?:\\.\\d+)?\\s*[;,]\\s*(?:url\\s*=\\s*)?['\"]?([^'\"]+?)['\"]?\\s*$", OPTS)
    private val JS_PATTERNS = listOf(
        Regex("(?:window|document|top|self)?\\.?location(?:\\.href)?\\s*=\\s*[\"']([^\"']+)[\"']", OPTS),
        Regex("location\\.(?:replace|assign)\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*\\)", OPTS),
    )

    fun findRedirect(html: String, includeScripts: Boolean = true): ClientRedirect? {
        for (tag in META_TAG.findAll(html)) {
            val text = tag.value
            if (!HTTP_EQUIV_REFRESH.containsMatchIn(text)) continue
            val contentMatch = CONTENT_ATTR.find(text) ?: continue
            val content = contentMatch.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: continue
            val urlMatch = REFRESH_URL.find(decodeEntities(content)) ?: continue
            val target = cleanup(urlMatch.groupValues[1])
            if (isAcceptable(target)) return ClientRedirect(HopKind.META_REFRESH, target)
        }
        if (!includeScripts) return null
        for (pattern in JS_PATTERNS) {
            for (match in pattern.findAll(html)) {
                val target = cleanup(match.groupValues[1])
                if (isAcceptable(target)) return ClientRedirect(HopKind.JS_REDIRECT, target)
            }
        }
        return null
    }

    private fun isAcceptable(target: String): Boolean =
        target.isNotEmpty() && !target.startsWith("#") &&
            (target.startsWith("http://", ignoreCase = true) ||
                target.startsWith("https://", ignoreCase = true) ||
                target.startsWith("/"))

    private fun cleanup(raw: String): String =
        decodeEntities(raw)
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\x26", "&")
            .trim()

    private fun decodeEntities(text: String): String =
        text.replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x2F;", "/", ignoreCase = true)
            .replace("&#47;", "/")
            .replace("&#x3D;", "=", ignoreCase = true)
            .replace("&#61;", "=")
}
