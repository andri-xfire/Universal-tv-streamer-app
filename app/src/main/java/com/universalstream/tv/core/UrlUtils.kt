package com.universalstream.tv.core

import java.net.IDN
import java.util.Locale

enum class UrlInvalidReason { EMPTY, CONTAINS_SPACES, UNSUPPORTED_SCHEME, MALFORMED, INVALID_HOST, INVALID_PORT }

sealed interface UrlParseResult {
    data class Valid(
        val url: String,
        val scheme: String,
        val host: String,
        val schemeAdded: Boolean,
    ) : UrlParseResult {
        val isHttps: Boolean get() = scheme == "https"
    }

    data class Invalid(val reason: UrlInvalidReason, val scheme: String? = null) : UrlParseResult
}

data class UrlParts(val scheme: String, val host: String, val port: Int?, val rest: String)

/**
 * Pure-Kotlin URL handling (no Android dependencies, unit-testable on the JVM).
 *
 * Accepts what people type on a remote: `example.com`, `www.example.com`, `http://…`,
 * `https://…`, `bit.ly/abc`, `host:8080/path`. Missing schemes default to HTTPS.
 * Only http/https are ever produced; `javascript:`, `file:`, `intent:`, `data:` etc. are rejected.
 * Credentials in the authority (`https://google.com@evil.com`) are dropped so the real host is shown.
 */
object UrlUtils {
    val WEB_SCHEMES = setOf("http", "https")

    private val SCHEME_PREFIX = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):")
    private val PORT_AND_REST = Regex("^(\\d{1,5})([/?#].*)?$")
    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val IPV6 = Regex("^\\[[0-9a-fA-F:.]+]$")
    private val LABEL = Regex("^[a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?$")

    fun parse(rawInput: String): UrlParseResult {
        val input = rawInput.trim()
        if (input.isEmpty()) return UrlParseResult.Invalid(UrlInvalidReason.EMPTY)
        if (input.any { it.isWhitespace() || it.isISOControl() }) {
            return UrlParseResult.Invalid(UrlInvalidReason.CONTAINS_SPACES)
        }

        val candidate: String
        val schemeAdded: Boolean
        val schemeMatch = SCHEME_PREFIX.find(input)
        when {
            input.startsWith("//") -> {
                candidate = "https:$input"
                schemeAdded = true
            }
            schemeMatch != null -> {
                val scheme = schemeMatch.groupValues[1].lowercase(Locale.ROOT)
                val rest = input.substring(schemeMatch.value.length)
                when {
                    scheme in WEB_SCHEMES -> {
                        if (!rest.startsWith("//")) {
                            return UrlParseResult.Invalid(UrlInvalidReason.MALFORMED, scheme)
                        }
                        candidate = scheme + ":" + rest
                        schemeAdded = false
                    }
                    // "example.com:8080/path" or "localhost:3000" — a host with a port, not a scheme.
                    !rest.startsWith("//") && PORT_AND_REST.matches(rest) -> {
                        candidate = "https://$input"
                        schemeAdded = true
                    }
                    else -> return UrlParseResult.Invalid(UrlInvalidReason.UNSUPPORTED_SCHEME, scheme)
                }
            }
            else -> {
                candidate = "https://$input"
                schemeAdded = true
            }
        }

        val parts = split(candidate) ?: return UrlParseResult.Invalid(UrlInvalidReason.MALFORMED)
        val host = normalizeHost(parts.host) ?: return UrlParseResult.Invalid(UrlInvalidReason.INVALID_HOST)
        val port = parts.port
        if (port != null && port !in 1..65535) return UrlParseResult.Invalid(UrlInvalidReason.INVALID_PORT)
        val authority = if (port != null) "$host:$port" else host
        val url = "${parts.scheme}://$authority${parts.rest}"
        return UrlParseResult.Valid(url = url, scheme = parts.scheme, host = host, schemeAdded = schemeAdded)
    }

    /** Splits `scheme://authority/rest`. Userinfo is discarded. Returns null when malformed. */
    fun split(url: String): UrlParts? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase(Locale.ROOT)
        val afterScheme = url.substring(schemeEnd + 3)
        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
            .let { if (it < 0) afterScheme.length else it }
        val authority = afterScheme.substring(0, authorityEnd).substringAfterLast('@')
        val rest = afterScheme.substring(authorityEnd)
        if (authority.isEmpty()) return null

        val host: String
        val port: Int?
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            host = authority.substring(0, close + 1)
            val after = authority.substring(close + 1)
            port = when {
                after.isEmpty() -> null
                after.startsWith(":") && after.length > 1 -> after.substring(1).toIntOrNull() ?: return null
                else -> return null
            }
        } else {
            val colon = authority.lastIndexOf(':')
            if (colon >= 0) {
                host = authority.substring(0, colon)
                val portText = authority.substring(colon + 1)
                port = if (portText.isEmpty()) null else portText.toIntOrNull() ?: return null
            } else {
                host = authority
                port = null
            }
        }
        return UrlParts(scheme, host, port, rest)
    }

    /** Returns the lower-case ASCII (punycode) host, or null if it is not a usable web host. */
    fun normalizeHost(rawHost: String): String? {
        if (rawHost.isEmpty()) return null
        if (IPV6.matches(rawHost)) return rawHost.lowercase(Locale.ROOT)
        val ascii = try {
            IDN.toASCII(rawHost, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT).trimEnd('.')
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (ascii.isEmpty() || ascii.length > 253) return null
        if (ascii == "localhost") return ascii
        IPV4.matchEntire(ascii)?.let { match ->
            return if (match.groupValues.drop(1).all { it.toInt() <= 255 }) ascii else null
        }
        val labels = ascii.split('.')
        if (labels.size < 2) return null
        if (labels.any { !LABEL.matches(it) }) return null
        val tld = labels.last()
        if (tld.length < 2 || tld.all { it.isDigit() }) return null
        return ascii
    }

    fun schemeOf(url: String): String? =
        SCHEME_PREFIX.find(url.trim())?.groupValues?.get(1)?.lowercase(Locale.ROOT)

    fun hostOf(url: String?): String? {
        if (url == null) return null
        val host = split(url.trim())?.host ?: return null
        return host.lowercase(Locale.ROOT).trimEnd('.').takeIf { it.isNotEmpty() }
    }

    fun isWebUrl(url: String?): Boolean = url != null && schemeOf(url) in WEB_SCHEMES

    fun isHttps(url: String?): Boolean = url != null && schemeOf(url) == "https"

    /** Coarse "same website" check used to decide whether a redirect needs a preview. */
    fun isSameSite(a: String, b: String): Boolean {
        val hostA = hostOf(a) ?: return false
        val hostB = hostOf(b) ?: return false
        return siteKey(hostA) == siteKey(hostB)
    }

    private fun siteKey(host: String): String = host.removePrefix("www.").removePrefix("m.")

    fun ellipsize(text: String, max: Int = 120): String =
        if (text.length <= max) text else text.take(max - 1) + "…"
}

/** Known URL shortening services. Any other shortener still works through generic redirect handling. */
object ShortenerRegistry {
    private val HOSTS = setOf(
        "bit.ly", "bitly.com", "bitly.is", "j.mp", "tinyurl.com", "tiny.one", "t.co", "is.gd", "v.gd",
        "cutt.ly", "ow.ly", "buff.ly", "rebrand.ly", "rb.gy", "shorturl.at", "shorturl.asia", "tiny.cc",
        "t.ly", "s.id", "bit.do", "lnkd.in", "goo.gl", "g.co", "youtu.be", "amzn.to", "qr.ae", "tr.im",
        "x.co", "soo.gd", "short.io", "urlz.fr", "bl.ink", "shorte.st", "clck.ru", "v.ht", "u.to",
        "1url.com", "hyperurl.co", "surl.li", "linkin.bio",
    )

    fun isKnownShortener(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase(Locale.ROOT).removePrefix("www.")
        return HOSTS.any { h == it || h.endsWith(".$it") }
    }
}
