package com.universalstream.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    private fun valid(input: String): UrlParseResult.Valid {
        val result = UrlUtils.parse(input)
        assertTrue("expected valid for '$input' but was $result", result is UrlParseResult.Valid)
        return result as UrlParseResult.Valid
    }

    private fun invalid(input: String): UrlInvalidReason {
        val result = UrlUtils.parse(input)
        assertTrue("expected invalid for '$input' but was $result", result is UrlParseResult.Invalid)
        return (result as UrlParseResult.Invalid).reason
    }

    @Test fun keepsExplicitHttps() = assertEquals("https://example.com", valid("https://example.com").url)

    @Test fun keepsExplicitHttp() {
        val r = valid("http://example.com/path?q=1")
        assertEquals("http://example.com/path?q=1", r.url)
        assertFalse(r.isHttps)
    }

    @Test fun addsHttpsToBareDomain() {
        val r = valid("example.com")
        assertEquals("https://example.com", r.url)
        assertTrue(r.schemeAdded)
    }

    @Test fun addsHttpsToWww() = assertEquals("https://www.example.com", valid("www.example.com").url)

    @Test fun shortLinkKeepsPath() = assertEquals("https://bit.ly/example123", valid("bit.ly/example123").url)

    @Test fun hostWithPortIsNotAScheme() = assertEquals("https://example.com:8080/a", valid("example.com:8080/a").url)

    @Test fun localhostWithPort() = assertEquals("https://localhost:3000", valid("localhost:3000").url)

    @Test fun trimsWhitespace() = assertEquals("https://example.com", valid("  example.com  ").url)

    @Test fun lowercasesSchemeAndHost() = assertEquals("https://example.com/Path", valid("HTTPS://Example.COM/Path").url)

    @Test fun dropsCredentialsAndShowsRealHost() {
        val r = valid("https://google.com@evil.example/login")
        assertEquals("evil.example", r.host)
        assertEquals("https://evil.example/login", r.url)
    }

    @Test fun internationalDomainBecomesPunycode() = assertEquals("xn--bcher-kva.example", valid("bücher.example").host)

    @Test fun ipv4() = assertEquals("https://192.168.1.10:8000/x", valid("192.168.1.10:8000/x").url)

    @Test fun rejectsBadIpv4() = assertEquals(UrlInvalidReason.INVALID_HOST, invalid("300.1.1.1"))

    @Test fun rejectsEmpty() = assertEquals(UrlInvalidReason.EMPTY, invalid("   "))

    @Test fun rejectsSpaces() = assertEquals(UrlInvalidReason.CONTAINS_SPACES, invalid("watch movies online"))

    @Test fun rejectsJavascript() = assertEquals(UrlInvalidReason.UNSUPPORTED_SCHEME, invalid("javascript:alert(1)"))

    @Test fun rejectsFile() = assertEquals(UrlInvalidReason.UNSUPPORTED_SCHEME, invalid("file:///sdcard/a.html"))

    @Test fun rejectsIntent() = assertEquals(UrlInvalidReason.UNSUPPORTED_SCHEME, invalid("intent://scan#Intent;end"))

    @Test fun rejectsFtp() = assertEquals(UrlInvalidReason.UNSUPPORTED_SCHEME, invalid("ftp://example.com"))

    @Test fun rejectsHostWithoutDot() = assertEquals(UrlInvalidReason.INVALID_HOST, invalid("example"))

    @Test fun rejectsBadPort() = assertEquals(UrlInvalidReason.INVALID_PORT, invalid("example.com:70000"))

    @Test fun rejectsHttpWithoutSlashes() = assertEquals(UrlInvalidReason.MALFORMED, invalid("http:example.com"))

    @Test fun sameSiteIgnoresWww() = assertTrue(UrlUtils.isSameSite("https://example.com/a", "https://www.example.com/b"))

    @Test fun differentSite() = assertFalse(UrlUtils.isSameSite("https://bit.ly/x", "https://example.com"))

    @Test fun knownShorteners() {
        assertTrue(ShortenerRegistry.isKnownShortener("bit.ly"))
        assertTrue(ShortenerRegistry.isKnownShortener("www.tinyurl.com"))
        assertTrue(ShortenerRegistry.isKnownShortener("t.co"))
        assertTrue(ShortenerRegistry.isKnownShortener("is.gd"))
        assertTrue(ShortenerRegistry.isKnownShortener("cutt.ly"))
        assertFalse(ShortenerRegistry.isKnownShortener("example.com"))
    }
}
