package com.universalstream.tv.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlRedirectParserTest {

    @Test fun metaRefreshDoubleQuotes() {
        val r = HtmlRedirectParser.findRedirect("""<html><head><meta http-equiv="refresh" content="0;url=https://example.com/a?b=1&amp;c=2"></head></html>""")
        assertEquals(ClientRedirect(HopKind.META_REFRESH, "https://example.com/a?b=1&c=2"), r)
    }

    @Test fun metaRefreshContentFirstAndQuotedUrl() {
        val r = HtmlRedirectParser.findRedirect("""<META content="5; URL='/next'" HTTP-EQUIV="Refresh">""")
        assertEquals(ClientRedirect(HopKind.META_REFRESH, "/next"), r)
    }

    @Test fun metaRefreshWithoutUrlIsIgnored() {
        assertNull(HtmlRedirectParser.findRedirect("""<meta http-equiv="refresh" content="30">"""))
    }

    @Test fun javascriptLocationReplace() {
        val r = HtmlRedirectParser.findRedirect("""<script>window.location.replace("https:\/\/example.com\/video");</script>""")
        assertEquals(ClientRedirect(HopKind.JS_REDIRECT, "https://example.com/video"), r)
    }

    @Test fun javascriptLocationHref() {
        val r = HtmlRedirectParser.findRedirect("""<script>location.href = 'https://example.com/x';</script>""")
        assertEquals(ClientRedirect(HopKind.JS_REDIRECT, "https://example.com/x"), r)
    }

    @Test fun scriptsIgnoredWhenDisabled() {
        assertNull(HtmlRedirectParser.findRedirect("""<script>location.href='https://example.com'</script>""", includeScripts = false))
    }

    @Test fun ignoresFragmentsAndJavascriptUrls() {
        assertNull(HtmlRedirectParser.findRedirect("""<script>location.href='#top'; location.href='javascript:void(0)'</script>"""))
    }
}
