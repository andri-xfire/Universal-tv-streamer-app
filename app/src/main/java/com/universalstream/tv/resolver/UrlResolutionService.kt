package com.universalstream.tv.resolver

import com.universalstream.tv.core.ShortenerRegistry
import com.universalstream.tv.core.UrlUtils
import com.universalstream.tv.core.UserAgentProvider

sealed interface ResolveOutcome {
    val originalUrl: String

    data class Resolved(
        override val originalUrl: String,
        val finalUrl: String,
        val redirectCount: Int,
        val usedBrowserFallback: Boolean,
        val downgradedToHttp: Boolean,
        val isShortLink: Boolean,
        /** True when the user should confirm: a short link, a different website, or plain HTTP. */
        val requiresPreview: Boolean,
    ) : ResolveOutcome {
        val isInsecure: Boolean get() = !UrlUtils.isHttps(finalUrl)
    }

    data class Failed(
        override val originalUrl: String,
        val error: ResolutionError,
        val detail: String?,
    ) : ResolveOutcome
}

/** HTTP resolution first; WebView fallback only when the chain stops on a shortener page. */
class UrlResolutionService(
    private val httpResolver: ShortUrlResolver,
    private val browserResolver: WebViewRedirectResolver,
    private val userAgentProvider: UserAgentProvider,
) {
    suspend fun resolve(url: String): ResolveOutcome {
        val isShortLink = ShortenerRegistry.isKnownShortener(UrlUtils.hostOf(url))
        return when (val result = httpResolver.resolve(url)) {
            is HttpResolution.Failure -> ResolveOutcome.Failed(url, result.error, result.detail)
            is HttpResolution.Success -> {
                var finalUrl = result.finalUrl
                var usedFallback = false
                var redirectCount = result.hops.size
                if (result.needsBrowserFallback) {
                    val viaBrowser = browserResolver.resolve(finalUrl, userAgentProvider.effectiveUserAgent())
                        ?: return ResolveOutcome.Failed(url, ResolutionError.CLIENT_REDIRECT_UNRESOLVED, null)
                    if (viaBrowser != finalUrl) {
                        finalUrl = viaBrowser
                        usedFallback = true
                        redirectCount += 1
                    }
                }
                val downgraded = result.downgradedToHttp || (UrlUtils.isHttps(url) && !UrlUtils.isHttps(finalUrl))
                val requiresPreview = isShortLink || !UrlUtils.isSameSite(url, finalUrl) || !UrlUtils.isHttps(finalUrl)
                ResolveOutcome.Resolved(
                    originalUrl = url,
                    finalUrl = finalUrl,
                    redirectCount = redirectCount,
                    usedBrowserFallback = usedFallback,
                    downgradedToHttp = downgraded,
                    isShortLink = isShortLink,
                    requiresPreview = requiresPreview,
                )
            }
        }
    }
}

object ResolutionMessages {
    fun describe(error: ResolutionError): String = when (error) {
        ResolutionError.INVALID_URL -> "The address is not a valid web link."
        ResolutionError.NETWORK_UNAVAILABLE -> "No internet connection. Check the TV's network settings and try again."
        ResolutionError.DNS_FAILURE -> "The website name could not be found (DNS lookup failed). Check the link for typos."
        ResolutionError.CONNECT_TIMEOUT -> "The link service did not answer in time (connection timeout)."
        ResolutionError.READ_TIMEOUT -> "The link service stopped responding (read timeout)."
        ResolutionError.SSL_ERROR -> "The link uses an invalid or untrusted security certificate, so it was not followed."
        ResolutionError.CONNECTION_FAILED -> "Could not connect to the link service."
        ResolutionError.REDIRECT_LOOP -> "The link redirects in a loop and never reaches a destination."
        ResolutionError.TOO_MANY_REDIRECTS -> "The link redirects too many times (more than ${ShortUrlResolver.DEFAULT_MAX_REDIRECTS})."
        ResolutionError.INVALID_REDIRECT_TARGET -> "The link points to an invalid address."
        ResolutionError.UNSUPPORTED_REDIRECT_SCHEME -> "The link tries to open another app instead of a website."
        ResolutionError.CLIENT_REDIRECT_UNRESOLVED ->
            "The link page did not reveal its destination. It may need a click, a CAPTCHA or a countdown."
    }
}
