package com.universalstream.tv.browser

/** HIDDEN: page uses the whole screen. PEEK: passive title bar after a page loads. ACTIVE: focusable controls. */
enum class ToolbarMode { HIDDEN, PEEK, ACTIVE }

enum class BrowserErrorType {
    NETWORK_UNAVAILABLE,
    DNS_FAILURE,
    TIMEOUT,
    CONNECTION_FAILED,
    SSL_ERROR,
    UNSUPPORTED_SCHEME,
    BAD_URL,
    TOO_MANY_REDIRECTS,
    RENDERER_CRASHED,
    GENERIC,
}

data class BrowserError(val type: BrowserErrorType, val url: String, val detail: String? = null)

enum class JsDialogType { ALERT, CONFIRM, PROMPT, BEFORE_UNLOAD }

class JsDialogRequest(
    val type: JsDialogType,
    val origin: String,
    val message: String,
    val defaultValue: String,
    val onConfirm: (String?) -> Unit,
    val onCancel: () -> Unit,
)

class PermissionPrompt(
    val origin: String,
    val resourceLabels: List<String>,
    val onGrant: () -> Unit,
    val onDeny: () -> Unit,
)

data class UiMessage(val id: Long, val text: String)

data class CursorState(val x: Float = 0f, val y: Float = 0f, val initialized: Boolean = false)

/** Callbacks from the WebView layer into the browser ViewModel. All are invoked on the main thread. */
interface BrowserListener {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String, title: String?, hadError: Boolean)
    fun onUrlChanged(url: String)
    fun onProgress(progress: Int)
    fun onTitle(title: String)
    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean)
    fun onMainFrameError(error: BrowserError)
    fun onHttpAccessDenied(statusCode: Int)
    fun onBlockedNavigation(url: String, scheme: String)
    fun onJsDialog(request: JsDialogRequest)
    fun onPermissionPrompt(prompt: PermissionPrompt)
    fun onPermissionPromptCancelled()
    fun onFullscreenChanged(isFullscreen: Boolean)
    fun onRendererGone(crashed: Boolean)
    fun onWebViewReplaced()
    fun onMediaPlaying(playing: Boolean)
    fun onMediaUnsupported()
    fun onMessage(text: String)
}
