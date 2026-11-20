package com.universalstream.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class UserAgentMode { DEFAULT, DESKTOP, MOBILE, CUSTOM }

/** CURSOR: the D-pad moves an on-screen pointer and OK clicks. DIRECT: keys go straight to the website. */
enum class NavigationMode { CURSOR, DIRECT }

data class AppSettings(
    val homePage: String = "",
    val openHomePageOnStartup: Boolean = false,
    val darkMode: Boolean = true,
    val javaScriptEnabled: Boolean = true,
    val textZoom: Int = 100,
    val userAgentMode: UserAgentMode = UserAgentMode.DEFAULT,
    val customUserAgent: String = "",
    val showToolbarOnPageLoad: Boolean = true,
    val navigationMode: NavigationMode = NavigationMode.CURSOR,
) {
    companion object {
        val ZOOM_LEVELS = listOf(75, 90, 100, 110, 125, 150, 175, 200)
    }
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val HOME_PAGE = stringPreferencesKey("home_page")
        val OPEN_HOME_ON_START = booleanPreferencesKey("open_home_on_start")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val JAVASCRIPT = booleanPreferencesKey("javascript")
        val TEXT_ZOOM = intPreferencesKey("text_zoom")
        val UA_MODE = stringPreferencesKey("ua_mode")
        val CUSTOM_UA = stringPreferencesKey("custom_ua")
        val SHOW_TOOLBAR = booleanPreferencesKey("show_toolbar_on_load")
        val NAV_MODE = stringPreferencesKey("navigation_mode")
    }

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                homePage = p[Keys.HOME_PAGE] ?: "",
                openHomePageOnStartup = p[Keys.OPEN_HOME_ON_START] ?: false,
                darkMode = p[Keys.DARK_MODE] ?: true,
                javaScriptEnabled = p[Keys.JAVASCRIPT] ?: true,
                textZoom = (p[Keys.TEXT_ZOOM] ?: 100).coerceIn(50, 300),
                userAgentMode = p[Keys.UA_MODE]?.let { runCatching { UserAgentMode.valueOf(it) }.getOrNull() }
                    ?: UserAgentMode.DEFAULT,
                customUserAgent = p[Keys.CUSTOM_UA] ?: "",
                showToolbarOnPageLoad = p[Keys.SHOW_TOOLBAR] ?: true,
                navigationMode = p[Keys.NAV_MODE]?.let { runCatching { NavigationMode.valueOf(it) }.getOrNull() }
                    ?: NavigationMode.CURSOR,
            )
        }
        .distinctUntilChanged()

    suspend fun setHomePage(url: String) {
        dataStore.edit { it[Keys.HOME_PAGE] = url }
    }

    suspend fun setOpenHomePageOnStartup(enabled: Boolean) {
        dataStore.edit { it[Keys.OPEN_HOME_ON_START] = enabled }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setJavaScriptEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.JAVASCRIPT] = enabled }
    }

    suspend fun setTextZoom(percent: Int) {
        dataStore.edit { it[Keys.TEXT_ZOOM] = percent.coerceIn(50, 300) }
    }

    suspend fun setUserAgent(mode: UserAgentMode, custom: String? = null) {
        dataStore.edit {
            it[Keys.UA_MODE] = mode.name
            if (custom != null) it[Keys.CUSTOM_UA] = custom.trim()
        }
    }

    suspend fun setShowToolbarOnPageLoad(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_TOOLBAR] = enabled }
    }

    suspend fun setNavigationMode(mode: NavigationMode) {
        dataStore.edit { it[Keys.NAV_MODE] = mode.name }
    }
}
