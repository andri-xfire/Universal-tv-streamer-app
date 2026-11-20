package com.universalstream.tv.browser

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hosts the view WebView hands us in onShowCustomView (HTML5 fullscreen video) on top of the
 * whole window, outside Compose, so the toolbar and every other UI element are hidden and the
 * video gets the full panel. Idempotent: safe to call [exit] from BACK and from onHideCustomView.
 */
class FullscreenManager(
    private val activity: Activity,
    private val onChanged: (Boolean) -> Unit,
) {
    private var customView: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var container: FrameLayout? = null

    val isFullscreen: Boolean get() = customView != null

    /** The fullscreen player view, used as the target for fallback remote keys. */
    val contentView: View? get() = customView

    fun enter(view: View, viewCallback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            viewCallback.onCustomViewHidden()
            return
        }
        val decor = activity.window.decorView as? FrameLayout ?: run {
            viewCallback.onCustomViewHidden()
            return
        }
        (view.parent as? ViewGroup)?.removeView(view)
        val holder = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            addView(view, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
        }
        decor.addView(holder, FrameLayout.LayoutParams(MATCH, MATCH))
        customView = view
        callback = viewCallback
        container = holder
        hideSystemBars()
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        onChanged(true)
    }

    fun exit() {
        if (customView == null) return
        val viewCallback = callback
        customView = null
        callback = null
        container?.let { holder ->
            holder.removeAllViews()
            (holder.parent as? ViewGroup)?.removeView(holder)
        }
        container = null
        hideSystemBars() // the app itself stays immersive on TV
        runCatching { viewCallback?.onCustomViewHidden() }
        onChanged(false)
    }

    private fun hideSystemBars() {
        val window = activity.window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
