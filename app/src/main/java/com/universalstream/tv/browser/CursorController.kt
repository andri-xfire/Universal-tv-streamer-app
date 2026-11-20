package com.universalstream.tv.browser

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/**
 * Pointer mode for websites that were never designed for a remote. The D-pad moves an on-screen
 * pointer (accelerating while held), pushing against an edge scrolls whatever is under the pointer
 * (mouse-wheel events), and OK sends a real tap to the WebView, which websites treat as a user
 * gesture (needed to start most video players).
 */
class CursorController(density: Float) {
    private val baseStepPx = 9f * density
    private val edgePx = 6f * density

    private val _state = MutableStateFlow(CursorState())
    val state: StateFlow<CursorState> = _state.asStateFlow()

    private var width = 0
    private var height = 0
    private var centerPressed = false

    fun onViewportChanged(newWidth: Int, newHeight: Int) {
        if (newWidth <= 0 || newHeight <= 0) return
        width = newWidth
        height = newHeight
        val current = _state.value
        _state.value = if (!current.initialized) {
            CursorState(newWidth / 2f, newHeight / 2f, initialized = true)
        } else {
            current.copy(
                x = current.x.coerceIn(0f, newWidth - 1f),
                y = current.y.coerceIn(0f, newHeight - 1f),
            )
        }
    }

    /** Returns true when the key was consumed by pointer mode. */
    fun handleKey(event: KeyEvent, target: WebView?): Boolean {
        val webView = target ?: return false
        if (width == 0 || height == 0) onViewportChanged(webView.width, webView.height)
        if (width == 0 || height == 0) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) move(event.keyCode, event.repeatCount, webView)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // Enter on a real keyboard keeps its normal meaning (submit a form field, new line…).
                if (event.keyCode != KeyEvent.KEYCODE_DPAD_CENTER && isAlphabeticKeyboard(event)) return false
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) centerPressed = true
                    KeyEvent.ACTION_UP -> {
                        if (centerPressed && !event.isCanceled) tap(webView)
                        centerPressed = false
                    }
                }
                true
            }
            else -> false
        }
    }

    private fun move(keyCode: Int, repeatCount: Int, webView: WebView) {
        val step = baseStepPx * min(1f + repeatCount * 0.45f, 7f)
        val scrollAmount = min(1f + repeatCount * 0.2f, 4f)
        val current = _state.value
        var x = current.x
        var y = current.y
        var vScroll = 0f
        var hScroll = 0f
        val maxX = width - 1f
        val maxY = height - 1f
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (y - step < edgePx) { y = edgePx; vScroll = scrollAmount } else y -= step
            KeyEvent.KEYCODE_DPAD_DOWN -> if (y + step > maxY - edgePx) { y = maxY - edgePx; vScroll = -scrollAmount } else y += step
            KeyEvent.KEYCODE_DPAD_LEFT -> if (x - step < edgePx) { x = edgePx; hScroll = -scrollAmount } else x -= step
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (x + step > maxX - edgePx) { x = maxX - edgePx; hScroll = scrollAmount } else x += step
        }
        _state.value = current.copy(x = x, y = y, initialized = true)
        if (vScroll != 0f || hScroll != 0f) {
            dispatchMouse(webView, MotionEvent.ACTION_SCROLL, x, y, vScroll, hScroll)
        } else {
            dispatchMouse(webView, MotionEvent.ACTION_HOVER_MOVE, x, y, 0f, 0f)
        }
    }

    private fun tap(webView: WebView) {
        val state = _state.value
        if (!webView.hasFocus()) webView.requestFocus()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, state.x, state.y, 0)
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN)
        webView.dispatchTouchEvent(down)
        down.recycle()
        webView.postDelayed({
            val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, state.x, state.y, 0)
            up.setSource(InputDevice.SOURCE_TOUCHSCREEN)
            webView.dispatchTouchEvent(up)
            up.recycle()
        }, TAP_DURATION_MS)
    }

    private fun dispatchMouse(webView: WebView, action: Int, x: Float, y: Float, vScroll: Float, hScroll: Float) {
        val now = SystemClock.uptimeMillis()
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            if (vScroll != 0f) setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
            if (hScroll != 0f) setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
        }
        val event = MotionEvent.obtain(
            now, now, action, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
        )
        webView.dispatchGenericMotionEvent(event)
        event.recycle()
    }

    private fun isAlphabeticKeyboard(event: KeyEvent): Boolean =
        event.device?.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC

    private companion object {
        const val TAP_DURATION_MS = 40L
    }
}
