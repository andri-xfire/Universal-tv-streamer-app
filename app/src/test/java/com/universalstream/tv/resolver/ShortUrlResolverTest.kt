package com.universalstream.tv.resolver

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShortUrlResolverTest {

    private lateinit var server: MockWebServer
    private val resolver = ShortUrlResolver(
        baseClient = OkHttpClient(),
        userAgent = { "UniversalStreamTV-Test" },
        connectTimeoutMs = 2_000,
        readTimeoutMs = 2_000,
    )

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun serve(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val response = handler(request)
                // A HEAD response carries the headers (incl. Content-Length) but never a body.
                return if (request.method == "HEAD") {
                    MockResponse().setStatus(response.status).setHeaders(response.headers)
                } else {
                    response
                }
            }
        }
    }

    private fun url(path: String) = server.url(path).toString()

    private fun redirect(code: Int, location: String) = MockResponse().setResponseCode(code).addHeader("Location", location)

    private fun page(body: String = "<html>ok</html>") =
        MockResponse().setResponseCode(200).addHeader("Content-Type", "text/html; charset=utf-8").setBody(body)

    @Test fun followsMultiHopChainAndKeepsQuery() = runBlocking {
        serve { req ->
            when (req.path) {
                "/s" -> redirect(301, "/a?x=1")
                "/a?x=1" -> redirect(302, "/b?x=1&y=2")
                "/b?x=1&y=2" -> redirect(307, url("/final?x=1&y=2"))
                "/final?x=1&y=2" -> page()
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = resolver.resolve(url("/s")) as HttpResolution.Success
        assertEquals(url("/final?x=1&y=2"), result.finalUrl)
        assertEquals(3, result.hops.size)
    }

    @Test fun supportsAllRedirectCodes() = runBlocking {
        val codes = listOf(301, 302, 303, 307, 308)
        serve { req ->
            val step = req.path!!.removePrefix("/r").toIntOrNull()
            if (step != null && step < codes.size) redirect(codes[step], "/r${step + 1}") else page()
        }
        val result = resolver.resolve(url("/r0")) as HttpResolution.Success
        assertEquals(url("/r5"), result.finalUrl)
        assertEquals(codes, result.hops.map { it.statusCode })
    }

    @Test fun fallsBackToGetWhenHeadNotAllowed() = runBlocking {
        serve { req ->
            when {
                req.method == "HEAD" -> MockResponse().setResponseCode(405)
                req.path == "/s" -> redirect(302, "/dest")
                else -> page()
            }
        }
        val result = resolver.resolve(url("/s")) as HttpResolution.Success
        assertEquals(url("/dest"), result.finalUrl)
        assertEquals("GET", result.hops.single().method)
    }

    @Test fun detectsRedirectLoop() = runBlocking {
        serve { req -> if (req.path == "/a") redirect(302, "/b") else redirect(302, "/a") }
        val result = resolver.resolve(url("/a")) as HttpResolution.Failure
        assertEquals(ResolutionError.REDIRECT_LOOP, result.error)
    }

    @Test fun limitsRedirectCount() = runBlocking {
        serve { req ->
            val n = req.path!!.removePrefix("/n").toInt()
            redirect(302, "/n${n + 1}")
        }
        val result = resolver.resolve(url("/n0")) as HttpResolution.Failure
        assertEquals(ResolutionError.TOO_MANY_REDIRECTS, result.error)
    }

    @Test fun rejectsAppSchemeRedirect() = runBlocking {
        serve { redirect(302, "intent://open#Intent;scheme=evil;end") }
        val result = resolver.resolve(url("/s")) as HttpResolution.Failure
        assertEquals(ResolutionError.UNSUPPORTED_REDIRECT_SCHEME, result.error)
    }

    @Test fun followsMetaRefresh() = runBlocking {
        serve { req ->
            when (req.path) {
                "/s" -> page("""<meta http-equiv="refresh" content="0; url=/landing">""")
                else -> page()
            }
        }
        val result = resolver.resolve(url("/s")) as HttpResolution.Success
        assertEquals(url("/landing"), result.finalUrl)
        assertEquals(HopKind.META_REFRESH, result.hops.single().kind)
    }

    @Test fun keepsFragmentAcrossRedirect() = runBlocking {
        serve { req -> if (req.path == "/s") redirect(301, "/d") else page() }
        val result = resolver.resolve(url("/s") + "#t=30") as HttpResolution.Success
        assertTrue(result.finalUrl.endsWith("/d#t=30"))
    }

    @Test fun noRedirectReturnsSameUrl() = runBlocking {
        serve { page() }
        val result = resolver.resolve(url("/plain")) as HttpResolution.Success
        assertEquals(url("/plain"), result.finalUrl)
        assertTrue(result.hops.isEmpty())
        assertFalse(result.needsBrowserFallback)
    }

    @Test fun invalidUrl() = runBlocking {
        val result = resolver.resolve("not a url") as HttpResolution.Failure
        assertEquals(ResolutionError.INVALID_URL, result.error)
    }
}
