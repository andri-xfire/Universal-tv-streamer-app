package com.universalstream.tv.resolver

import com.universalstream.tv.core.ShortenerRegistry
import com.universalstream.tv.core.UrlUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ResolutionError {
    INVALID_URL,
    NETWORK_UNAVAILABLE,
    DNS_FAILURE,
    CONNECT_TIMEOUT,
    READ_TIMEOUT,
    SSL_ERROR,
    CONNECTION_FAILED,
    REDIRECT_LOOP,
    TOO_MANY_REDIRECTS,
    INVALID_REDIRECT_TARGET,
    UNSUPPORTED_REDIRECT_SCHEME,
    CLIENT_REDIRECT_UNRESOLVED,
}

data class RedirectHop(
    val fromUrl: String,
    val toUrl: String,
    val statusCode: Int,
    val method: String,
    val kind: HopKind,
)

sealed interface HttpResolution {
    val originalUrl: String
    val hops: List<RedirectHop>

    data class Success(
        override val originalUrl: String,
        val finalUrl: String,
        val finalStatusCode: Int,
        override val hops: List<RedirectHop>,
        /** The chain ended on a shortener page with no detectable redirect: try the WebView fallback. */
        val needsBrowserFallback: Boolean,
        val downgradedToHttp: Boolean,
    ) : HttpResolution

    data class Failure(
        override val originalUrl: String,
        val error: ResolutionError,
        val detail: String?,
        override val hops: List<RedirectHop>,
    ) : HttpResolution
}

/**
 * Resolves short links by following the redirect chain manually with OkHttp.
 *
 * - Follows 301/302/303/307/308 one hop at a time (automatic redirects are disabled so every
 *   destination can be validated and only http/https targets are accepted).
 * - Tries HEAD first and falls back to GET when HEAD is rejected (405/501/403/…) or the server
 *   breaks the connection. Never relies on HEAD alone.
 * - Scans HTML (first 64 KB) for meta-refresh and simple JavaScript redirects.
 * - Detects loops, caps the number of hops, keeps query strings and carries the fragment over.
 * - Uses a per-resolution in-memory cookie jar (some shorteners set a cookie before redirecting).
 * It never solves CAPTCHAs or defeats bot protection: such pages simply end the chain.
 */
class ShortUrlResolver(
    baseClient: OkHttpClient,
    private val userAgent: suspend () -> String,
    private val isNetworkAvailable: () -> Boolean = { true },
    private val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
    connectTimeoutMs: Long = 10_000,
    readTimeoutMs: Long = 15_000,
) {
    private val client: OkHttpClient = baseClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(connectTimeoutMs + readTimeoutMs + 5_000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun resolve(startUrl: String): HttpResolution = withContext(Dispatchers.IO) {
        val start = startUrl.toHttpUrlOrNull()
            ?: return@withContext HttpResolution.Failure(startUrl, ResolutionError.INVALID_URL, null, emptyList())
        val ua = userAgent()
        val session = client.newBuilder().cookieJar(InMemoryCookieJar()).build()
        val hops = mutableListOf<RedirectHop>()
        val visited = HashSet<String>()
        var current = start
        var downgraded = false

        while (true) {
            currentCoroutineContext().ensureActive()
            if (!visited.add(canonicalKey(current))) {
                return@withContext HttpResolution.Failure(startUrl, ResolutionError.REDIRECT_LOOP, current.toString(), hops.toList())
            }
            val step = try {
                fetch(session, current, ua)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                return@withContext HttpResolution.Failure(startUrl, mapError(e), e.message, hops.toList())
            }
            when (step) {
                is Step.Final -> {
                    val needsFallback = step.isHtml && step.statusCode in 200..299 &&
                        ShortenerRegistry.isKnownShortener(current.host)
                    return@withContext HttpResolution.Success(
                        originalUrl = startUrl,
                        finalUrl = current.toString(),
                        finalStatusCode = step.statusCode,
                        hops = hops.toList(),
                        needsBrowserFallback = needsFallback,
                        downgradedToHttp = downgraded,
                    )
                }
                is Step.Redirect -> {
                    val next = when (val target = resolveTarget(current, step.location)) {
                        is Target.Ok -> target.url
                        is Target.Rejected ->
                            return@withContext HttpResolution.Failure(startUrl, target.error, step.location, hops.toList())
                    }
                    hops += RedirectHop(current.toString(), next.toString(), step.statusCode, step.method, step.kind)
                    if (hops.size > maxRedirects) {
                        return@withContext HttpResolution.Failure(startUrl, ResolutionError.TOO_MANY_REDIRECTS, null, hops.toList())
                    }
                    if (current.isHttps && !next.isHttps) downgraded = true
                    current = next
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private sealed interface Step {
        data class Redirect(val statusCode: Int, val location: String, val method: String, val kind: HopKind) : Step
        data class Final(val statusCode: Int, val isHtml: Boolean) : Step
    }

    private sealed interface Target {
        data class Ok(val url: HttpUrl) : Target
        data class Rejected(val error: ResolutionError) : Target
    }

    private suspend fun fetch(client: OkHttpClient, url: HttpUrl, ua: String): Step {
        val knownShortener = ShortenerRegistry.isKnownShortener(url.host)
        val head: Response? = try {
            client.newCall(request(url, "HEAD", ua)).await()
        } catch (e: IOException) {
            if (isRetryableWithGet(e)) null else throw e
        }
        if (head != null) {
            head.use { response ->
                val code = response.code
                val location = response.header("Location")
                if (code in REDIRECT_CODES && !location.isNullOrBlank()) {
                    return Step.Redirect(code, location, "HEAD", HopKind.HTTP_REDIRECT)
                }
                if (code in 200..299) {
                    val html = isHtml(response.header("Content-Type"), assumeHtmlWhenMissing = false)
                    val length = response.header("Content-Length")?.toLongOrNull()
                    val worthScanning = html && (knownShortener || (length != null && length in 0..SMALL_PAGE_BYTES))
                    if (!worthScanning) return Step.Final(code, html)
                }
                // Otherwise HEAD is unsupported/blocked or the page body must be inspected: use GET.
            }
        }

        return client.newCall(request(url, "GET", ua)).await().use { response ->
            val code = response.code
            val location = response.header("Location")
            if (code in REDIRECT_CODES && !location.isNullOrBlank()) {
                return@use Step.Redirect(code, location, "GET", HopKind.HTTP_REDIRECT)
            }
            val html = isHtml(response.header("Content-Type"), assumeHtmlWhenMissing = true)
            if (html) {
                val body = response.peekBody(MAX_SCAN_BYTES).string()
                val includeScripts = knownShortener || body.length <= SMALL_SCRIPT_PAGE_CHARS
                val found = HtmlRedirectParser.findRedirect(body, includeScripts)
                if (found != null) return@use Step.Redirect(code, found.target, "GET", found.kind)
            }
            Step.Final(code, html)
        }
    }

    private fun request(url: HttpUrl, method: String, ua: String): Request =
        Request.Builder()
            .url(url)
            .method(method, null)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
            .build()

    private fun resolveTarget(base: HttpUrl, rawLocation: String): Target {
        val location = rawLocation.trim()
        if (location.isEmpty()) return Target.Rejected(ResolutionError.INVALID_REDIRECT_TARGET)
        val scheme = UrlUtils.schemeOf(location)
        if (scheme != null && scheme !in UrlUtils.WEB_SCHEMES) {
            // e.g. intent://, market://, javascript: — never followed.
            return Target.Rejected(ResolutionError.UNSUPPORTED_REDIRECT_SCHEME)
        }
        val resolved = base.resolve(location) ?: return Target.Rejected(ResolutionError.INVALID_REDIRECT_TARGET)
        val hostOk = resolved.host.contains(':') || UrlUtils.normalizeHost(resolved.host) != null
        if (!hostOk) return Target.Rejected(ResolutionError.INVALID_REDIRECT_TARGET)
        // RFC 9110: a redirect without a fragment inherits the original fragment.
        val withFragment = if (resolved.fragment == null && base.fragment != null) {
            resolved.newBuilder().fragment(base.fragment).build()
        } else {
            resolved
        }
        return Target.Ok(withFragment)
    }

    private fun canonicalKey(url: HttpUrl): String = url.newBuilder().fragment(null).build().toString()

    private fun isRetryableWithGet(e: IOException): Boolean =
        e is ProtocolException || e is EOFException ||
            (e.message?.contains("unexpected end of stream", ignoreCase = true) == true)

    private fun isHtml(contentType: String?, assumeHtmlWhenMissing: Boolean): Boolean =
        if (contentType == null) assumeHtmlWhenMissing else contentType.contains("html", ignoreCase = true)

    private fun mapError(e: IOException): ResolutionError = when (e) {
        is UnknownHostException ->
            if (isNetworkAvailable()) ResolutionError.DNS_FAILURE else ResolutionError.NETWORK_UNAVAILABLE
        is SocketTimeoutException ->
            if (e.message?.contains("connect", ignoreCase = true) == true) ResolutionError.CONNECT_TIMEOUT
            else ResolutionError.READ_TIMEOUT
        is SSLException -> ResolutionError.SSL_ERROR
        is InterruptedIOException -> ResolutionError.READ_TIMEOUT
        is ConnectException, is NoRouteToHostException ->
            if (isNetworkAvailable()) ResolutionError.CONNECTION_FAILED else ResolutionError.NETWORK_UNAVAILABLE
        else -> if (isNetworkAvailable()) ResolutionError.CONNECTION_FAILED else ResolutionError.NETWORK_UNAVAILABLE
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        })
    }

    private class InMemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path } }
            this.cookies.addAll(cookies)
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt < now }
            return cookies.filter { it.matches(url) }
        }
    }

    companion object {
        const val DEFAULT_MAX_REDIRECTS = 10
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private const val MAX_SCAN_BYTES = 64L * 1024
        private const val SMALL_PAGE_BYTES = 8L * 1024
        private const val SMALL_SCRIPT_PAGE_CHARS = 4 * 1024
    }
}
