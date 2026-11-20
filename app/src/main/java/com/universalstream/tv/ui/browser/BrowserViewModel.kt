package com.universalstream.tv.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalstream.tv.browser.BrowserError
import com.universalstream.tv.browser.BrowserErrorType
import com.universalstream.tv.browser.BrowserListener
import com.universalstream.tv.browser.JsDialogRequest
import com.universalstream.tv.browser.PermissionPrompt
import com.universalstream.tv.browser.ToolbarMode
import com.universalstream.tv.browser.UiMessage
import com.universalstream.tv.core.UrlUtils
import com.universalstream.tv.data.AppSettings
import com.universalstream.tv.data.BrowserRepository
import com.universalstream.tv.data.NavigationMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserUiState(
    val url: String = "",
    val title: String = "",
    val progress: Int = 100,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = true,
    val toolbarMode: ToolbarMode = ToolbarMode.HIDDEN,
    /** Bumped on every toolbar key press to restart its auto-hide timer. */
    val toolbarInteraction: Int = 0,
    val isFullscreen: Boolean = false,
    val cursorEnabled: Boolean = true,
    val error: BrowserError? = null,
    val message: UiMessage? = null,
    val jsDialog: JsDialogRequest? = null,
    val permissionPrompt: PermissionPrompt? = null,
    val mediaPlaying: Boolean = false,
    val webViewGeneration: Int = 0,
) {
    val hasSession: Boolean get() = UrlUtils.isWebUrl(url)
}

class BrowserViewModel(private val repository: BrowserRepository) : ViewModel(), BrowserListener {

    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private var settings = AppSettings()
    private var messageCounter = 0L
    private var unsupportedMediaNotified = false

    @OptIn(ExperimentalCoroutinesApi::class)
    val isFavorite: StateFlow<Boolean> = _state
        .map { it.url }
        .distinctUntilChanged()
        .flatMapLatest { url -> if (UrlUtils.isWebUrl(url)) repository.isFavorite(url) else flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun updateSettings(newSettings: AppSettings) {
        val navigationChanged = newSettings.navigationMode != settings.navigationMode
        settings = newSettings
        if (navigationChanged) {
            _state.update { it.copy(cursorEnabled = newSettings.navigationMode == NavigationMode.CURSOR) }
        }
    }

    fun restorableUrl(): String? = _state.value.url.takeIf { UrlUtils.isWebUrl(it) }

    fun onNewSession(url: String) {
        unsupportedMediaNotified = false
        _state.update {
            it.copy(
                url = url,
                title = "",
                error = null,
                canGoBack = false,
                canGoForward = false,
                isLoading = true,
                progress = 5,
                jsDialog = null,
                permissionPrompt = null,
                cursorEnabled = settings.navigationMode == NavigationMode.CURSOR,
            )
        }
    }

    // ------------------------------------------------------------ UI actions

    fun toggleToolbar() {
        _state.update {
            it.copy(
                toolbarMode = if (it.toolbarMode == ToolbarMode.ACTIVE) ToolbarMode.HIDDEN else ToolbarMode.ACTIVE,
                toolbarInteraction = it.toolbarInteraction + 1,
            )
        }
    }

    fun setToolbarMode(mode: ToolbarMode) {
        _state.update { it.copy(toolbarMode = mode) }
    }

    fun hidePeek() {
        _state.update { if (it.toolbarMode == ToolbarMode.PEEK) it.copy(toolbarMode = ToolbarMode.HIDDEN) else it }
    }

    fun onToolbarInteraction() {
        _state.update { it.copy(toolbarInteraction = it.toolbarInteraction + 1) }
    }

    fun toggleCursor() {
        val enabled = !_state.value.cursorEnabled
        _state.update { it.copy(cursorEnabled = enabled) }
        showMessage(
            if (enabled) "Pointer mode: move with the D-pad, press OK to click."
            else "Direct mode: the D-pad goes straight to the website.",
        )
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun toggleFavorite() {
        val current = _state.value
        if (!UrlUtils.isWebUrl(current.url)) return
        viewModelScope.launch {
            val added = repository.toggleFavorite(current.url, current.title.ifBlank { UrlUtils.hostOf(current.url) ?: current.url })
            showMessage(if (added) "Added to favorites" else "Removed from favorites")
        }
    }

    fun resolveJsDialog(confirmed: Boolean, text: String?) {
        val dialog = _state.value.jsDialog ?: return
        _state.update { it.copy(jsDialog = null) }
        if (confirmed) dialog.onConfirm(text) else dialog.onCancel()
    }

    fun resolvePermission(granted: Boolean) {
        val prompt = _state.value.permissionPrompt ?: return
        _state.update { it.copy(permissionPrompt = null) }
        if (granted) prompt.onGrant() else prompt.onDeny()
    }

    fun showMessage(text: String) {
        val id = ++messageCounter
        _state.update { it.copy(message = UiMessage(id, text)) }
    }

    fun dismissMessage(id: Long) {
        _state.update { if (it.message?.id == id) it.copy(message = null) else it }
    }

    // ------------------------------------------------------------ BrowserListener

    override fun onPageStarted(url: String) {
        unsupportedMediaNotified = false
        _state.update {
            it.copy(
                url = if (UrlUtils.isWebUrl(url)) url else it.url,
                isLoading = true,
                progress = if (it.isLoading) maxOf(it.progress, 5) else 5,
                error = null,
                isSecure = if (UrlUtils.isWebUrl(url)) UrlUtils.isHttps(url) else it.isSecure,
                toolbarMode = if (settings.showToolbarOnPageLoad && it.toolbarMode == ToolbarMode.HIDDEN && !it.isFullscreen) {
                    ToolbarMode.PEEK
                } else {
                    it.toolbarMode
                },
            )
        }
    }

    override fun onPageFinished(url: String, title: String?, hadError: Boolean) {
        _state.update {
            it.copy(
                isLoading = false,
                progress = 100,
                title = title?.takeIf { t -> t.isNotBlank() && t != url } ?: it.title,
            )
        }
        if (!hadError && UrlUtils.isWebUrl(url)) {
            viewModelScope.launch { repository.recordVisit(url, title?.takeIf { it != url }) }
        }
    }

    override fun onUrlChanged(url: String) {
        _state.update { it.copy(url = url, isSecure = UrlUtils.isHttps(url)) }
    }

    override fun onProgress(progress: Int) {
        _state.update { it.copy(progress = progress, isLoading = progress < 100) }
    }

    override fun onTitle(title: String) {
        _state.update { it.copy(title = title) }
        val url = _state.value.url
        if (UrlUtils.isWebUrl(url)) viewModelScope.launch { repository.updateTitle(url, title) }
    }

    override fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
        _state.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    override fun onMainFrameError(error: BrowserError) {
        _state.update { it.copy(error = error, isLoading = false, toolbarMode = ToolbarMode.HIDDEN) }
    }

    override fun onHttpAccessDenied(statusCode: Int) {
        showMessage("The website denied access (HTTP $statusCode). It may need a sign-in or be unavailable in your region.")
    }

    override fun onBlockedNavigation(url: String, scheme: String) {
        showMessage("Blocked a link that tries to open another app or a local file ($scheme:).")
    }

    override fun onJsDialog(request: JsDialogRequest) {
        if (_state.value.jsDialog != null) {
            request.onCancel() // one dialog at a time: stops pages from stacking pop-ups
            return
        }
        _state.update { it.copy(jsDialog = request) }
    }

    override fun onPermissionPrompt(prompt: PermissionPrompt) {
        _state.value.permissionPrompt?.onDeny()
        _state.update { it.copy(permissionPrompt = prompt) }
    }

    override fun onPermissionPromptCancelled() {
        _state.update { it.copy(permissionPrompt = null) }
    }

    override fun onFullscreenChanged(isFullscreen: Boolean) {
        _state.update {
            it.copy(isFullscreen = isFullscreen, toolbarMode = if (isFullscreen) ToolbarMode.HIDDEN else it.toolbarMode)
        }
    }

    override fun onRendererGone(crashed: Boolean) {
        if (crashed) {
            _state.update {
                it.copy(error = BrowserError(BrowserErrorType.RENDERER_CRASHED, it.url), isLoading = false, isFullscreen = false)
            }
        } else {
            showMessage("The page was reloaded to free up memory.")
        }
    }

    override fun onWebViewReplaced() {
        _state.update {
            it.copy(webViewGeneration = it.webViewGeneration + 1, canGoBack = false, canGoForward = false, isFullscreen = false)
        }
    }

    override fun onMediaPlaying(playing: Boolean) {
        _state.update { it.copy(mediaPlaying = playing) }
    }

    override fun onMediaUnsupported() {
        if (unsupportedMediaNotified) return
        unsupportedMediaNotified = true
        showMessage(
            "This video format may not be supported by this TV's WebView. " +
                "Try another source on the site, or update Android System WebView.",
        )
    }

    override fun onMessage(text: String) = showMessage(text)
}
