package com.universalstream.tv.core

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import androidx.webkit.WebViewCompat

/**
 * What the app knows about the TV it runs on. Used to pick a lightweight profile on
 * 1 GB devices such as the Xiaomi Mi TV Stick, and shown in Settings > About.
 *
 * The app never forces a rendering resolution: WebView renders at the window size the system
 * gives the app (often 1080p UI even on a 4K panel), which keeps low-end hardware smooth.
 */
data class DeviceProfile(
    val sdkInt: Int,
    val androidRelease: String,
    val manufacturer: String,
    val model: String,
    val uiWidthPx: Int,
    val uiHeightPx: Int,
    val densityDpi: Int,
    val panelWidthPx: Int,
    val panelHeightPx: Int,
    val memoryClassMb: Int,
    val totalRamMb: Long,
    val isLowRamDevice: Boolean,
    val webViewPackage: String?,
    val webViewVersion: String?,
    val isTelevision: Boolean,
    val cpuCores: Int,
) {
    /** Low-memory profile: fewer animations, smaller history, shorter polling, eager WebView release. */
    val isLowPerformance: Boolean
        get() = isLowRamDevice || totalRamMb in 1..1_700 || memoryClassMb in 1..128

    val isUhdPanel: Boolean get() = maxOf(panelWidthPx, panelHeightPx) >= 3_200

    val webViewMajorVersion: Int? get() = webViewVersion?.substringBefore('.')?.toIntOrNull()

    /** Very old WebView builds miss modern video/JS features; the About screen suggests updating. */
    val isOutdatedWebView: Boolean get() = (webViewMajorVersion ?: 0) in 1..99

    companion object {
        fun detect(context: Context): DeviceProfile {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }

            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display: Display? = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            val metrics = DisplayMetrics()
            if (display != null) {
                @Suppress("DEPRECATION")
                display.getRealMetrics(metrics)
            } else {
                metrics.setTo(context.resources.displayMetrics)
            }
            var panelWidth = metrics.widthPixels
            var panelHeight = metrics.heightPixels
            display?.supportedModes?.forEach { mode ->
                if (mode.physicalWidth.toLong() * mode.physicalHeight > panelWidth.toLong() * panelHeight) {
                    panelWidth = mode.physicalWidth
                    panelHeight = mode.physicalHeight
                }
            }

            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val webViewPackage = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()

            return DeviceProfile(
                sdkInt = Build.VERSION.SDK_INT,
                androidRelease = Build.VERSION.RELEASE ?: "",
                manufacturer = Build.MANUFACTURER ?: "",
                model = Build.MODEL ?: "",
                uiWidthPx = metrics.widthPixels,
                uiHeightPx = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
                panelWidthPx = panelWidth,
                panelHeightPx = panelHeight,
                memoryClassMb = activityManager.memoryClass,
                totalRamMb = memoryInfo.totalMem / (1024L * 1024L),
                isLowRamDevice = activityManager.isLowRamDevice,
                webViewPackage = webViewPackage?.packageName,
                webViewVersion = webViewPackage?.versionName,
                isTelevision = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION,
                cpuCores = Runtime.getRuntime().availableProcessors(),
            )
        }
    }
}
