package com.universalstream.tv.browser

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.universalstream.tv.BuildConfig
import com.universalstream.tv.core.DeviceProfile
import com.universalstream.tv.core.NetworkMonitor
import com.universalstream.tv.core.UrlUtils
import com.universalstream.tv.core.UserAgents
import com.universalstream.tv.data.AppSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URISyntaxException

/**
 * Owns the app's single long-lived WebView (Activity scoped, never recreated per navigation).
 *
 * - Compose hosts it through AndroidView; leaving the browser screen only detaches and pauses it.
 * - It is destroyed and recreated only when the renderer dies or the system is short on memory
 *   while the page is not visible, and then restores the last URL.
 * - All WebViewClient/WebChromeClient policy (navigation filtering, errors, SSL, fullscreen,
 *   dialogs, permissions) lives here; UI state is pushed to [BrowserListener].
 */
class BrowserController(
    private val activity: ComponentActivity,
    private val listener: BrowserListener,
    private val deviceProfile: DeviceProfile,
    private val networkMonitor: NetworkMonitor,
) {
    private var webView: WebView? = null
    private var settings = AppSettings()
    private var defaultUserAgent: String? = null

    private var browserVisible = false
    private var activityStarted = true
    private var timersPaused = false
    private var pendingClearHistory = false
    private var currentMainFrameUrl: String? = null
    private var mainFrameErrorReported = false
    private var currentErrorUrl: String? = null
    private var lastErrorRetryable = false
    private var mediaPlaying = false
    private var mediaPollJob: Job? = null
    private val grantedProtectedMediaOrigins = HashSet<String>()

    /** After a renderer crash the new WebView stays blank until the user chooses Retry. */
    private var holdAutoRestore = false

    /** Last real web page shown; used to restore after WebView recreation. */
    var lastKnownUrl: String? = null
        private set

    val fullscreen = FullscreenManager(activity) { isFullscreen ->
        listener.onFullscreenChanged(isFullscreen)
        updateKeepScreenOn()
        if (!isFullscreen) webView?.requestFocus()
    }

    val cursor = CursorController(activity.resources.displayMetrics.density)

    private val emptyPoster: Bitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }
    private val webViewClient = Client()
    private val chromeClient = ChromeClient()

    init {
        activity.lifecycleScope.launch {
            networkMonitor.isOnline.drop(1).collect { online ->
                if (online && lastErrorRetryable) {
                    lastErrorRetryable = false
                    retry()
                }
            }
        }
    }

    // ---------------------------------------------------------------- WebView lifecycle

    fun currentWebView(): WebView? = webView

    fun hasSession(): Boolean = lastKnownUrl != null

    fun restoreSession(url: String?) {
        if (lastKnownUrl == null && UrlUtils.isWebUrl(url)) lastKnownUrl = url
    }

    /** Called by the AndroidView factory. Reuses the existing instance whenever possible. */
    fun webViewForComposition(): WebView {
        val existing = webView
        val wv = existing ?: createWebView().also { created ->
            webView = created
            if (!holdAutoRestore) lastKnownUrl?.let { created.loadUrl(it) }
        }
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.post { if (wv.isFocusable) wv.requestFocus() }
        return wv
    }

    fun onCompositionReleased(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    fun open(url: String, newSession: Boolean) {
        val wv = webView ?: createWebView().also { webView = it }
        lastErrorRetryable = false
        currentErrorUrl = null
        holdAutoRestore = false
        if (newSession) pendingClearHistory = true
        lastKnownUrl = url
        wv.loadUrl(url)
    }

    fun resumeSession() {
        if (webView == null) lastKnownUrl?.let { open(it, newSession = false) }
    }

    fun goBack(): Boolean {
        val wv = webView ?: return false
        if (!wv.canGoBack()) return false
        wv.goBack()
        return true
    }

    fun goForward() {
        webView?.let { if (it.canGoForward()) it.goForward() }
    }

    fun reload() {
        val wv = webView
        if (wv == null || currentErrorUrl != null) retry() else wv.reload()
    }

    fun stopLoading() {
        webView?.stopLoading()
    }

    fun retry() {
        val url = currentErrorUrl ?: lastKnownUrl ?: return
        currentErrorUrl = null
        holdAutoRestore = false
        val wv = webView
        if (wv == null) open(url, newSession = false) else wv.loadUrl(url)
    }

    fun currentUrl(): String? = webView?.url?.takeIf { UrlUtils.isWebUrl(it) } ?: lastKnownUrl

    fun setContentFocusable(focusable: Boolean) {
        val wv = webView ?: return
        wv.isFocusable = focusable
        wv.isFocusableInTouchMode = focusable
        if (focusable) wv.requestFocus() else wv.clearFocus()
    }

    fun setBrowserVisible(visible: Boolean) {
        if (browserVisible == visible) return
        browserVisible = visible
        val wv = webView
        if (visible) {
            wv?.onResume()
            startMediaPolling()
        } else {
            pauseMedia()
            fullscreen.exit()
            wv?.onPause()
            stopMediaPolling()
            setMediaPlaying(false)
        }
        updateKeepScreenOn()
    }

    fun onActivityStart() {
        activityStarted = true
        val wv = webView
        if (timersPaused && wv != null) {
            wv.resumeTimers()
            timersPaused = false
        }
        if (browserVisible) {
            wv?.onResume()
            startMediaPolling()
        }
        updateKeepScreenOn()
    }

    fun onActivityStop() {
        activityStarted = false
        pauseMedia()
        fullscreen.exit()
        webView?.let {
            it.onPause()
            it.pauseTimers() // global: stops JS timers of all WebViews while the app is in the background
            timersPaused = true
        }
        stopMediaPolling()
        setMediaPlaying(false)
        updateKeepScreenOn()
        CookieManager.getInstance().flush()
    }

    @Suppress("DEPRECATION")
    fun onTrimMemory(level: Int) {
        val inBackground = level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        val pressure = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        if (inBackground || (pressure && !browserVisible)) {
            // The page is not being watched: free the renderer. It reloads lastKnownUrl on return.
            releaseWebView(rendererAlive = true)
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            webView?.clearCache(false) // in-memory cache only
        }
    }

    fun destroy() {
        stopMediaPolling()
        fullscreen.exit()
        releaseWebView(rendererAlive = true, notify = false)
    }

    private fun releaseWebView(rendererAlive: Boolean, notify: Boolean = true) {
        val wv = webView ?: return
        webView = null
        fullscreen.exit()
        (wv.parent as? ViewGroup)?.removeView(wv)
        runCatching {
            if (rendererAlive) {
                wv.stopLoading()
                wv.loadUrl("about:blank")
            }
            wv.removeAllViews()
            wv.destroy()
        }
        if (notify) listener.onWebViewReplaced()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val wv = WebView(activity)
        wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.setBackgroundColor(Color.BLACK)
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.overScrollMode = View.OVER_SCROLL_NEVER
        applyWebSettings(wv)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Embedded players (iframes) commonly need their own cookies to play.
            setAcceptThirdPartyCookies(wv, true)
        }
        wv.webViewClient = webViewClient
        wv.webChromeClient = chromeClient
        wv.setDownloadListener { _, _, _, _, _ ->
            listener.onMessage("Downloads are not supported. Universal Stream TV only plays videos inside the website.")
        }
        // Keep the renderer important while visible; let the system reclaim it when hidden.
        wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        wv.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            cursor.onViewportChanged(view.width, view.height)
        }
        if (timersPaused && activityStarted) {
            wv.resumeTimers()
            timersPaused = false
        }
        return wv
    }

    // ---------------------------------------------------------------- settings

    fun applySettings(newSettings: AppSettings) {
        settings = newSettings
        webView?.let { applyWebSettings(it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyWebSettings(wv: WebView) {
        val ws = wv.settings
        ws.javaScriptEnabled = settings.javaScriptEnabled
        ws.domStorageEnabled = true
        ws.mediaPlaybackRequiresUserGesture = false // lets remote PLAY start a video
        ws.javaScriptCanOpenWindowsAutomatically = false
        ws.setSupportMultipleWindows(false) // target=_blank opens in this same WebView
        ws.allowFileAccess = false
        ws.allowContentAccess = false
        @Suppress("DEPRECATION")
        ws.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        ws.allowUniversalAccessFromFileURLs = false
        ws.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        ws.loadWithOverviewMode = true
        ws.useWideViewPort = true
        ws.setSupportZoom(true)
        ws.builtInZoomControls = false
        ws.displayZoomControls = false
        ws.textZoom = settings.textZoom
        ws.cacheMode = WebSettings.LOAD_DEFAULT
        ws.setGeolocationEnabled(false)
        ws.offscreenPreRaster = false
        val defaultUa = defaultUserAgent ?: WebSettings.getDefaultUserAgent(activity).also { defaultUserAgent = it }
        val ua = UserAgents.forMode(settings.userAgentMode, defaultUa, settings.customUserAgent)
        if (ws.userAgentString != (ua ?: defaultUa)) ws.userAgentString = ua
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(ws, true)
        }
    }

    // ---------------------------------------------------------------- storage

    fun clearCache() {
        val wv = webView
        if (wv != null) {
            wv.clearCache(true)
        } else {
            val temp = WebView(activity)
            temp.clearCache(true)
            temp.destroy()
        }
    }

    fun clearCookiesAndSiteData(onDone: () -> Unit) {
        WebStorage.getInstance().deleteAllData()
        webView?.clearFormData()
        grantedProtectedMediaOrigins.clear()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            onDone()
        }
    }

    // ---------------------------------------------------------------- media & remote keys

    fun handleMediaKey(keyCode: Int) {
        val wv = webView ?: return
        val (action, seconds) = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "toggle" to 0
            KeyEvent.KEYCODE_MEDIA_PLAY -> "play" to 0
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> "pause" to 0
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> "seek" to SEEK_SECONDS
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> "seek" to -SEEK_SECONDS
            else -> return
        }
        if (!settings.javaScriptEnabled) {
            dispatchFallbackKey(keyCode)
            return
        }
        wv.evaluateJavascript(MediaScripts.command(action, seconds)) { raw ->
            if (raw?.trim('"') != "ok") dispatchFallbackKey(keyCode)
        }
    }

    /**
     * When no top-level <video> is reachable (e.g. a cross-origin iframe player), forward a key the
     * player is likely to understand: arrows seek and Space toggles in most web players.
     */
    private fun dispatchFallbackKey(keyCode: Int) {
        val target: View = fullscreen.contentView ?: webView ?: return
        val fallback = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> KeyEvent.KEYCODE_DPAD_RIGHT
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> KeyEvent.KEYCODE_DPAD_LEFT
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE ->
                if (fullscreen.isFullscreen) KeyEvent.KEYCODE_SPACE else keyCode
            else -> keyCode
        }
        val now = SystemClock.uptimeMillis()
        target.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, fallback, 0))
        target.dispatchKeyEvent(KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, fallback, 0))
    }

    private fun pauseMedia() {
        if (settings.javaScriptEnabled) webView?.evaluateJavascript(MediaScripts.PAUSE_ALL, null)
    }

    private fun startMediaPolling() {
        if (mediaPollJob?.isActive == true || !browserVisible || !activityStarted) return
        val interval = if (deviceProfile.isLowPerformance) 20_000L else 15_000L
        mediaPollJob = activity.lifecycleScope.launch {
            delay(5_000)
            while (isActive) {
                pollMedia()
                delay(interval)
            }
        }
    }

    private fun stopMediaPolling() {
        mediaPollJob?.cancel()
        mediaPollJob = null
    }

    private fun pollMedia() {
        val wv = webView ?: return
        if (!settings.javaScriptEnabled) return
        wv.evaluateJavascript(MediaScripts.STATUS) { raw ->
            val value = raw?.trim('"') ?: return@evaluateJavascript
            setMediaPlaying(value.getOrNull(0) == '1')
            if (value.getOrNull(1) == '1') listener.onMediaUnsupported()
        }
    }

    private fun setMediaPlaying(playing: Boolean) {
        if (mediaPlaying == playing) return
        mediaPlaying = playing
        listener.onMediaPlaying(playing)
        updateKeepScreenOn()
    }

    /** Keep the TV awake only while a video is fullscreen or actually playing. */
    private fun updateKeepScreenOn() {
        val keep = browserVisible && activityStarted && (fullscreen.isFullscreen || mediaPlaying)
        if (keep) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ---------------------------------------------------------------- navigation policy

    private fun handleMainFrameNavigation(url: String): Boolean {
        return when (val scheme = UrlUtils.schemeOf(url)) {
            "http", "https" -> false
            "about" -> url != "about:blank"
            "intent" -> {
                handleIntentScheme(url)
                true
            }
            null -> false
            else -> {
                // javascript:, file:, content:, data:, market:, mailto:, tel:, custom app schemes…
                listener.onBlockedNavigation(url, scheme)
                true
            }
        }
    }

    /** intent:// links are never launched; only their http(s) browser_fallback_url is honoured. */
    private fun handleIntentScheme(url: String) {
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (e: URISyntaxException) {
            null
        }
        val fallback = intent?.getStringExtra("browser_fallback_url")
        if (fallback != null && UrlUtils.isWebUrl(fallback)) {
            webView?.loadUrl(fallback)
        } else {
            listener.onBlockedNavigation(url, "intent")
        }
    }

    private fun updateNavigationState(view: WebView) {
        listener.onNavigationStateChanged(view.canGoBack(), view.canGoForward())
    }

    private fun mapWebError(code: Int): BrowserErrorType {
        val online = isOnlineNow()
        return when (code) {
            WebViewClient.ERROR_HOST_LOOKUP ->
                if (online) BrowserErrorType.DNS_FAILURE else BrowserErrorType.NETWORK_UNAVAILABLE
            WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_IO ->
                if (online) BrowserErrorType.CONNECTION_FAILED else BrowserErrorType.NETWORK_UNAVAILABLE
            WebViewClient.ERROR_TIMEOUT -> BrowserErrorType.TIMEOUT
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> BrowserErrorType.SSL_ERROR
            WebViewClient.ERROR_UNSUPPORTED_SCHEME -> BrowserErrorType.UNSUPPORTED_SCHEME
            WebViewClient.ERROR_BAD_URL -> BrowserErrorType.BAD_URL
            WebViewClient.ERROR_REDIRECT_LOOP -> BrowserErrorType.TOO_MANY_REDIRECTS
            else -> if (online) BrowserErrorType.GENERIC else BrowserErrorType.NETWORK_UNAVAILABLE
        }
    }

    private fun isOnlineNow(): Boolean = networkMonitor.isCurrentlyOnline()

    private fun describeSslError(error: SslError?): String = when (error?.primaryError) {
        SslError.SSL_UNTRUSTED -> "The website's certificate is not issued by a trusted authority."
        SslError.SSL_EXPIRED -> "The website's certificate has expired."
        SslError.SSL_IDMISMATCH -> "The certificate does not belong to this website name."
        SslError.SSL_NOTYETVALID -> "The website's certificate is not valid yet. Check the TV's date and time."
        SslError.SSL_DATE_INVALID -> "The certificate date is invalid. Check the TV's date and time."
        else -> "The website's security certificate is invalid."
    }

    private fun handleRenderProcessGone(view: WebView, crashed: Boolean): Boolean {
        if (view != webView) {
            (view.parent as? ViewGroup)?.removeView(view)
            runCatching { view.destroy() }
            return true
        }
        val restoreUrl = lastKnownUrl
        if (crashed) holdAutoRestore = true
        releaseWebView(rendererAlive = false)
        setMediaPlaying(false)
        if (crashed) {
            // Do not reload automatically: a page that crashes the renderer would loop.
            currentErrorUrl = restoreUrl
            holdAutoRestore = true
            listener.onRendererGone(crashed = true)
        } else {
            // Killed by the system to reclaim memory: reload silently if the user is watching.
            listener.onRendererGone(crashed = false)
            if (browserVisible && restoreUrl != null) open(restoreUrl, newSession = false)
        }
        return true
    }

    private fun originOf(url: String?): String {
        if (url == null) return "This page"
        return UrlUtils.hostOf(url) ?: "This page"
    }

    private inner class Client : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val req = request ?: return false
            val url = req.url?.toString() ?: return false
            if (!req.isForMainFrame) {
                // Sub-frames may use about:/data:/blob:, but never launch apps or local files.
                return when (UrlUtils.schemeOf(url)) {
                    "http", "https", "about", "data", "blob", null -> false
                    else -> true
                }
            }
            return handleMainFrameNavigation(url)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (view == null || view != webView) return
            currentMainFrameUrl = url
            mainFrameErrorReported = false
            currentErrorUrl = null // a new load clears the previous error; onReceivedError sets it again
            listener.onPageStarted(url ?: "")
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (view == null || view != webView || url == null) return
            if (pendingClearHistory && url != "about:blank") {
                // A newly opened site starts a fresh history: BACK from it returns Home, not to the old site.
                view.clearHistory()
                pendingClearHistory = false
            }
            if (UrlUtils.isWebUrl(url) && !mainFrameErrorReported) lastKnownUrl = url
            listener.onPageFinished(url, view.title, mainFrameErrorReported)
            updateNavigationState(view)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            if (view == null || view != webView) return
            if (url != null && UrlUtils.isWebUrl(url)) {
                lastKnownUrl = url
                listener.onUrlChanged(url)
            }
            updateNavigationState(view)
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            val req = request ?: return
            if (view == null || view != webView || !req.isForMainFrame) return
            val description = error?.description?.toString()
            if (description?.contains("ERR_ABORTED") == true) return // navigation replaced by another one
            val type = mapWebError(error?.errorCode ?: WebViewClient.ERROR_UNKNOWN)
            val url = req.url?.toString() ?: currentMainFrameUrl ?: ""
            mainFrameErrorReported = true
            currentErrorUrl = url
            lastErrorRetryable = type in RETRYABLE
            listener.onMainFrameError(BrowserError(type, url, description))
        }

        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
            val req = request ?: return
            if (view == null || view != webView || !req.isForMainFrame) return
            val code = errorResponse?.statusCode ?: return
            if (code == 401 || code == 403 || code == 451) listener.onHttpAccessDenied(code)
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            handler?.cancel() // Never proceed: certificate errors are not bypassed.
            if (view == null || view != webView) return
            val errorUrl = error?.url
            val isMainFrame = errorUrl == null || errorUrl == currentMainFrameUrl ||
                UrlUtils.hostOf(errorUrl) == UrlUtils.hostOf(currentMainFrameUrl)
            if (isMainFrame) {
                mainFrameErrorReported = true
                currentErrorUrl = errorUrl ?: currentMainFrameUrl
                lastErrorRetryable = false
                listener.onMainFrameError(
                    BrowserError(BrowserErrorType.SSL_ERROR, errorUrl ?: currentMainFrameUrl ?: "", describeSslError(error)),
                )
            } else {
                listener.onMessage("Some content on this page was blocked because of an invalid security certificate.")
            }
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            val target = view ?: return true
            return handleRenderProcessGone(target, detail?.didCrash() ?: true)
        }
    }

    private inner class ChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (view != null && view == webView) listener.onProgress(newProgress)
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (view != null && view == webView && !title.isNullOrBlank()) listener.onTitle(title)
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view == null || callback == null) return
            if (!browserVisible) {
                callback.onCustomViewHidden()
                return
            }
            fullscreen.enter(view, callback)
        }

        override fun onHideCustomView() {
            fullscreen.exit()
        }

        /** Hides WebView's default grey "play" poster on videos without a poster image. */
        override fun getDefaultVideoPoster(): Bitmap = emptyPoster

        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            val res = result ?: return false
            listener.onJsDialog(
                JsDialogRequest(JsDialogType.ALERT, originOf(url), message.orEmpty(), "",
                    onConfirm = { res.confirm() }, onCancel = { res.cancel() }),
            )
            return true
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            val res = result ?: return false
            listener.onJsDialog(
                JsDialogRequest(JsDialogType.CONFIRM, originOf(url), message.orEmpty(), "",
                    onConfirm = { res.confirm() }, onCancel = { res.cancel() }),
            )
            return true
        }

        override fun onJsPrompt(
            view: WebView?,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult?,
        ): Boolean {
            val res = result ?: return false
            listener.onJsDialog(
                JsDialogRequest(JsDialogType.PROMPT, originOf(url), message.orEmpty(), defaultValue.orEmpty(),
                    onConfirm = { text -> res.confirm(text ?: "") }, onCancel = { res.cancel() }),
            )
            return true
        }

        override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            val res = result ?: return false
            listener.onJsDialog(
                JsDialogRequest(JsDialogType.BEFORE_UNLOAD, originOf(url),
                    "Leave this page? Changes you made may not be saved.", "",
                    onConfirm = { res.confirm() }, onCancel = { res.cancel() }),
            )
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            val req = request ?: return
            val origin = req.origin?.toString() ?: ""
            // Camera, microphone and MIDI are not available in this app (no such manifest permissions).
            val grantable = req.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
            if (grantable.isEmpty()) {
                req.deny()
                listener.onMessage("This website asked for camera, microphone or device access, which this TV app does not provide.")
                return
            }
            if (origin in grantedProtectedMediaOrigins) {
                req.grant(grantable.toTypedArray())
                return
            }
            listener.onPermissionPrompt(
                PermissionPrompt(
                    origin = originOf(origin),
                    resourceLabels = listOf("Play protected (DRM) videos using a protected media identifier"),
                    onGrant = {
                        grantedProtectedMediaOrigins += origin
                        req.grant(grantable.toTypedArray())
                    },
                    onDeny = { req.deny() },
                ),
            )
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
            listener.onPermissionPromptCancelled()
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            callback?.invoke(origin, false, false)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            val message = consoleMessage?.message() ?: return true
            if (MediaScripts.isUnsupportedMediaMessage(message)) listener.onMediaUnsupported()
            // Debug builds only, truncated: page logs can contain tokens and must not reach release logcat.
            if (BuildConfig.DEBUG) Log.d(TAG, "console: ${message.take(200)}")
            return true
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            filePathCallback?.onReceiveValue(null)
            listener.onMessage("File uploads are not supported on TV.")
            return true
        }
    }

    private companion object {
        const val TAG = "BrowserController"
        const val SEEK_SECONDS = 10
        val RETRYABLE = setOf(
            BrowserErrorType.NETWORK_UNAVAILABLE,
            BrowserErrorType.DNS_FAILURE,
            BrowserErrorType.TIMEOUT,
            BrowserErrorType.CONNECTION_FAILED,
        )
    }
}
