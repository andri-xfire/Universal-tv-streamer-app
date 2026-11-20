package com.universalstream.tv.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Screen { Home, Browser, Settings }

/** Top-level navigation. Three screens do not justify a navigation library. */
class AppViewModel : ViewModel() {
    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    fun show(screen: Screen) {
        _screen.value = screen
    }
}
