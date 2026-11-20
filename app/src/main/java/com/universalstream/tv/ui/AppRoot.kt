package com.universalstream.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalstream.tv.browser.BrowserController
import com.universalstream.tv.core.DeviceProfile
import com.universalstream.tv.ui.browser.BrowserScreen
import com.universalstream.tv.ui.browser.BrowserViewModel
import com.universalstream.tv.ui.home.HomeScreen
import com.universalstream.tv.ui.home.HomeViewModel
import com.universalstream.tv.ui.settings.SettingsScreen
import com.universalstream.tv.ui.settings.SettingsViewModel

@Composable
fun AppRoot(
    appViewModel: AppViewModel,
    homeViewModel: HomeViewModel,
    browserViewModel: BrowserViewModel,
    settingsViewModel: SettingsViewModel,
    controller: BrowserController,
    deviceProfile: DeviceProfile,
    onOpenUrl: (String) -> Unit,
    onExitApp: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val screen by appViewModel.screen.collectAsStateWithLifecycle()
    val browserState by browserViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(screen) { controller.setBrowserVisible(screen == Screen.Browser) }
    LaunchedEffect(Unit) { homeViewModel.openRequests.collect { url -> onOpenUrl(url) } }

    when (screen) {
        Screen.Home -> HomeScreen(
            viewModel = homeViewModel,
            session = browserState.takeIf { it.hasSession },
            onContinueSession = {
                controller.resumeSession()
                appViewModel.show(Screen.Browser)
            },
            onOpenSettings = { appViewModel.show(Screen.Settings) },
            onExitApp = onExitApp,
        )
        Screen.Browser -> BrowserScreen(
            viewModel = browserViewModel,
            controller = controller,
            onHome = { appViewModel.show(Screen.Home) },
            onEditUrl = { url ->
                homeViewModel.setInput(url)
                appViewModel.show(Screen.Home)
            },
            onCopyUrl = onCopyUrl,
            onOpenExternal = onOpenExternal,
        )
        Screen.Settings -> SettingsScreen(
            viewModel = settingsViewModel,
            deviceProfile = deviceProfile,
            onBack = { appViewModel.show(Screen.Home) },
            onClearCache = controller::clearCache,
            onClearCookies = { onDone -> controller.clearCookiesAndSiteData(onDone) },
        )
    }
}
