package com.universalstream.tv.resolver

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.universalstream.tv.core.ShortenerRegistry
import com.universalstream.tv.core.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Fallback for short links that redirect with JavaScript or other client-side navigation that
 * the HTTP resolver could not see. A short-lived, off-screen WebView loads the link and reports
 * the first main-frame navigation that leaves the shortener. Images are blocked and media cannot
 * autoplay, the WebView is destroyed as soon as a result (or the timeout) is reached, and it is
 * only created on demand, so the app keeps a single long-lived WebView.
 *
 * It does not interact with the page: CAPTCHAs, sign-in walls and countdown interstitials are
 * not bypassed — they simply end in "could not resolve", and the user can open the link itself.
 */
class WebViewRedirectResolver(private val context: Context) {

    private class Tracker(var lastUrl: String)

    suspend fun resolve(
        url: String,
        userAgent: String?,
        timeoutMs: Long = 15_000L,
        settleMs: Long = 4_000L,
    ): String? = withContext(Dispatchers.Main) {
        val tracker = Tracker(url)
        val result = withTimeoutOrNull(timeoutMs) { runResolution(url, userAgent, settleMs, tracker) }
        result ?: tracker.lastUrl.takeIf { isUsefulDestination(it, url) }
    }

    private fun isUsefulDestination(candidate: String, startUrl: String): Boolean =
        candidate != startUrl && UrlUtils.isWebUrl(candidate) &&
            !ShortenerRegistry.isKnownShortener(UrlUtils.hostOf(candidate))

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runResolution(
        startUrl: String,
        userAgent: String?,
        settleMs: Long,
        tracker: Tracker,
    ): String? = suspendCancellableCoroutine { continuation ->
        val mainHandler = Handler(Looper.getMainLooper())
        val webView = WebView(context)
        var finished = false

        fun cleanup() {
            mainHandler.removeCallbacksAndMessages(null)
            runCatching {
                webView.stopLoading()
                webView.webViewClient = WebViewClient()
                webView.destroy()
            }
        }

        fun finish(result: String?) {
            if (finished) return
            finished = true
            cleanup()
            if (continuation.isActive) continuation.resume(result)
        }

        fun candidate(): String? = tracker.lastUrl.takeIf { it != startUrl && UrlUtils.isWebUrl(it) }

        val settle = Runnable { finish(candidate()) }

        continuation.invokeOnCancellation {
            mainHandler.post {
                if (!finished) {
                    finished = true
                    cleanup()
                }
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = true
            loadsImagesAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            if (!userAgent.isNullOrBlank()) userAgentString = userAgent
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val req = request ?: return false
                if (!req.isForMainFrame) return false
                val target = req.url?.toString() ?: return false
                if (!UrlUtils.isWebUrl(target)) {
                    // App links (intent:, market: …) are not followed.
                    finish(candidate())
                    return true
                }
                tracker.lastUrl = target
                if (target != startUrl && !ShortenerRegistry.isKnownShortener(UrlUtils.hostOf(target))) {
                    finish(target)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                mainHandler.removeCallbacks(settle)
                val pageUrl = url ?: return
                if (pageUrl != startUrl && UrlUtils.isWebUrl(pageUrl)) {
                    tracker.lastUrl = pageUrl
                    if (!ShortenerRegistry.isKnownShortener(UrlUtils.hostOf(pageUrl))) finish(pageUrl)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                mainHandler.removeCallbacks(settle)
                mainHandler.postDelayed(settle, settleMs)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel() // never proceed past certificate errors
                finish(null)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) finish(candidate())
            }
        }

        webView.loadUrl(startUrl)
    }
}
