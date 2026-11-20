package com.universalstream.tv.browser

import android.view.KeyEvent
import com.universalstream.tv.ui.browser.BrowserViewModel

/**
 * Routes remote-control keys while the browser screen is visible. Only the keys the app owns
 * are intercepted; everything else reaches the WebView (or the fullscreen player) untouched.
 *
 * - BACK: short press -> [onBack] (exit fullscreen > close overlay > history back > Home);
 *   hold ~0.7 s -> toggle the toolbar (for remotes without a MENU key, e.g. some Xiaomi remotes).
 * - MENU / INFO: toggle the toolbar.
 * - PLAY/PAUSE, FAST FORWARD, REWIND, STOP: control the page's video.
 * - D-pad / OK: moves and clicks the pointer when pointer mode is active; otherwise passed through.
 */
class RemoteKeyHandler(
    private val controller: BrowserController,
    private val viewModel: BrowserViewModel,
    private val onBack: () -> Unit,
) {
    private var backDownTracked = false
    private var backLongHandled = false

    fun handle(event: KeyEvent): Boolean {
        val code = event.keyCode
        val isFullscreen = controller.fullscreen.isFullscreen
        return when {
            code == KeyEvent.KEYCODE_BACK -> handleBack(event, isFullscreen)
            code in TOOLBAR_KEYS -> {
                if (isFullscreen) return false
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) viewModel.toggleToolbar()
                true
            }
            code in MEDIA_KEYS -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) controller.handleMediaKey(code)
                true
            }
            isPointerActive(isFullscreen) -> controller.cursor.handleKey(event, controller.currentWebView())
            else -> false
        }
    }

    private fun isPointerActive(isFullscreen: Boolean): Boolean {
        val state = viewModel.state.value
        return state.cursorEnabled && !isFullscreen && state.toolbarMode != ToolbarMode.ACTIVE && state.error == null
    }

    private fun handleBack(event: KeyEvent, isFullscreen: Boolean): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    backDownTracked = true
                    backLongHandled = false
                } else if (backDownTracked && !backLongHandled && !isFullscreen &&
                    event.eventTime - event.downTime >= LONG_PRESS_MS
                ) {
                    backLongHandled = true
                    viewModel.toggleToolbar()
                }
            }
            KeyEvent.ACTION_UP -> {
                if (backDownTracked && !backLongHandled && !event.isCanceled) onBack()
                backDownTracked = false
                backLongHandled = false
            }
        }
        return true
    }

    private companion object {
        const val LONG_PRESS_MS = 700L
        val TOOLBAR_KEYS = setOf(KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO)
        val MEDIA_KEYS = setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        )
    }
}
