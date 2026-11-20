package com.universalstream.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalstream.tv.data.AppSettings
import com.universalstream.tv.data.BrowserRepository
import com.universalstream.tv.data.NavigationMode
import com.universalstream.tv.data.SettingsRepository
import com.universalstream.tv.data.UserAgentMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val browserRepository: BrowserRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setHomePage(url: String) = launch { repository.setHomePage(url) }
    fun setOpenHomePageOnStartup(enabled: Boolean) = launch { repository.setOpenHomePageOnStartup(enabled) }
    fun setDarkMode(enabled: Boolean) = launch { repository.setDarkMode(enabled) }
    fun setJavaScriptEnabled(enabled: Boolean) = launch { repository.setJavaScriptEnabled(enabled) }
    fun setTextZoom(percent: Int) = launch { repository.setTextZoom(percent) }
    fun setUserAgent(mode: UserAgentMode, custom: String? = null) = launch { repository.setUserAgent(mode, custom) }
    fun setShowToolbarOnPageLoad(enabled: Boolean) = launch { repository.setShowToolbarOnPageLoad(enabled) }
    fun setNavigationMode(mode: NavigationMode) = launch { repository.setNavigationMode(mode) }
    fun clearHistory() = launch { browserRepository.clearHistory() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
