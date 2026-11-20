package com.universalstream.tv.core

import android.content.Context
import android.webkit.WebSettings
import com.universalstream.tv.data.SettingsRepository
import com.universalstream.tv.data.UserAgentMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object UserAgents {
    private val CHROME_VERSION = Regex("Chrome/([\\d.]+)")
    private val WEBVIEW_VERSION_TOKEN = Regex("Version/\\d+(\\.\\d+)* ")

    /** Desktop Chrome UA built from the device's real Chrome version, so it never goes stale. */
    fun desktop(defaultUa: String): String {
        val version = CHROME_VERSION.find(defaultUa)?.groupValues?.get(1) ?: "131.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
    }

    /** Mobile Chrome UA: the WebView UA without the "; wv" marker. */
    fun mobile(defaultUa: String): String {
        var ua = defaultUa.replace("; wv)", ")").replace(WEBVIEW_VERSION_TOKEN, "")
        if (!ua.contains(" Mobile ")) ua = ua.replace(" Safari/", " Mobile Safari/")
        return ua
    }

    /** Returns the UA to set on WebView, or null to keep the WebView default. */
    fun forMode(mode: UserAgentMode, defaultUa: String, custom: String): String? = when (mode) {
        UserAgentMode.DEFAULT -> null
        UserAgentMode.DESKTOP -> desktop(defaultUa)
        UserAgentMode.MOBILE -> mobile(defaultUa)
        UserAgentMode.CUSTOM -> custom.trim().takeIf { it.isNotEmpty() }
    }
}

/** Supplies the same User-Agent to the HTTP resolver that the WebView will use. */
class UserAgentProvider(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    @Volatile
    private var cachedDefault: String? = null

    suspend fun defaultUserAgent(): String {
        cachedDefault?.let { return it }
        val ua = withContext(Dispatchers.Main) {
            runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
        } ?: FALLBACK_UA
        cachedDefault = ua
        return ua
    }

    suspend fun effectiveUserAgent(): String {
        val settings = settingsRepository.settings.first()
        val default = defaultUserAgent()
        return UserAgents.forMode(settings.userAgentMode, default, settings.customUserAgent) ?: default
    }

    private companion object {
        const val FALLBACK_UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
