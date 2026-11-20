package com.universalstream.tv

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.universalstream.tv.browser.BrowserController
import com.universalstream.tv.browser.RemoteKeyHandler
import com.universalstream.tv.browser.ToolbarMode
import com.universalstream.tv.core.UrlParseResult
import com.universalstream.tv.core.UrlUtils
import com.universalstream.tv.ui.AppRoot
import com.universalstream.tv.ui.AppViewModel
import com.universalstream.tv.ui.Screen
import com.universalstream.tv.ui.browser.BrowserViewModel
import com.universalstream.tv.ui.home.HomeViewModel
import com.universalstream.tv.ui.settings.SettingsViewModel
import com.universalstream.tv.ui.theme.UniversalStreamTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as UniversalStreamApp).container

    private val appViewModel: AppViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                HomeViewModel(
                    repository = container.browserRepository,
                    resolver = container.urlResolutionService,
                    settingsRepository = container.settingsRepository,
                    historyRowSize = if (container.deviceProfile.isLowPerformance) 15 else 30,
                )
            }
        }
    }
    private val browserViewModel: BrowserViewModel by viewModels {
        viewModelFactory { initializer { BrowserViewModel(container.browserRepository) } }
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        viewModelFactory { initializer { SettingsViewModel(container.settingsRepository, container.browserRepository) } }
    }

    private lateinit var controller: BrowserController
    private lateinit var remoteKeys: RemoteKeyHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()

        controller = BrowserController(this, browserViewModel, container.deviceProfile, container.networkMonitor)
        controller.restoreSession(browserViewModel.restorableUrl())
        remoteKeys = RemoteKeyHandler(controller, browserViewModel, onBack = ::handleBrowserBack)

        lifecycleScope.launch {
            container.settingsRepository.settings.collect { settings ->
                controller.applySettings(settings)
                browserViewModel.updateSettings(settings)
            }
        }

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val settings = container.settingsRepository.settings.first()
                if (settings.openHomePageOnStartup) {
                    (UrlUtils.parse(settings.homePage) as? UrlParseResult.Valid)?.let { openUrl(it.url) }
                }
            }
        }

        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            UniversalStreamTheme(
                darkTheme = settings.darkMode,
                reduceMotion = container.deviceProfile.isLowPerformance,
            ) {
                AppRoot(
                    appViewModel = appViewModel,
                    homeViewModel = homeViewModel,
                    browserViewModel = browserViewModel,
                    settingsViewModel = settingsViewModel,
                    controller = controller,
                    deviceProfile = container.deviceProfile,
                    onOpenUrl = ::openUrl,
                    onExitApp = ::finish,
                    onCopyUrl = ::copyToClipboard,
                    onOpenExternal = ::openInExternalBrowser,
                )
            }
        }
    }

    /** Remote keys are routed here first while the browser is visible; other screens use Compose focus. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (appViewModel.screen.value == Screen.Browser && remoteKeys.handle(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        controller.onActivityStart()
    }

    override fun onStop() {
        controller.onActivityStop()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        controller.onTrimMemory(level)
    }

    override fun onDestroy() {
        controller.destroy()
        super.onDestroy()
    }

    private fun openUrl(url: String) {
        browserViewModel.onNewSession(url)
        controller.open(url, newSession = true)
        appViewModel.show(Screen.Browser)
    }

    /**
     * BACK priority in the browser:
     * 1. exit fullscreen video, 2. close the toolbar/overlay, 3. go back in page history, 4. return Home.
     * (JavaScript and permission dialogs are separate windows and consume BACK themselves.)
     */
    private fun handleBrowserBack() {
        if (controller.fullscreen.isFullscreen) {
            controller.fullscreen.exit()
            return
        }
        val state = browserViewModel.state.value
        if (state.toolbarMode == ToolbarMode.ACTIVE) {
            browserViewModel.setToolbarMode(ToolbarMode.HIDDEN)
            return
        }
        if (state.toolbarMode == ToolbarMode.PEEK) browserViewModel.setToolbarMode(ToolbarMode.HIDDEN)
        if (state.error != null) {
            browserViewModel.clearError()
            if (!controller.goBack()) appViewModel.show(Screen.Home)
            return
        }
        if (controller.goBack()) return
        appViewModel.show(Screen.Home)
    }

    private fun copyToClipboard(text: String) {
        if (text.isBlank()) return
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
        browserViewModel.showMessage("Link copied")
    }

    private fun openInExternalBrowser(url: String) {
        if (!UrlUtils.isWebUrl(url)) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            browserViewModel.showMessage("No other browser is installed on this TV.")
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
