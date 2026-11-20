package com.universalstream.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalstream.tv.BuildConfig
import com.universalstream.tv.core.DeviceProfile
import com.universalstream.tv.core.UrlParseResult
import com.universalstream.tv.core.UrlUtils
import com.universalstream.tv.data.AppSettings
import com.universalstream.tv.data.NavigationMode
import com.universalstream.tv.data.UserAgentMode
import com.universalstream.tv.ui.components.TvButton
import com.universalstream.tv.ui.components.TvDialog
import com.universalstream.tv.ui.components.TvListRow
import com.universalstream.tv.ui.components.requestFocusSafely
import com.universalstream.tv.ui.theme.TvSafeArea
import kotlinx.coroutines.delay

private enum class SettingsDialog {
    HOME_PAGE, NAVIGATION, ZOOM, USER_AGENT, CUSTOM_UA, CLEAR_HISTORY, CLEAR_CACHE, CLEAR_COOKIES, ABOUT
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    deviceProfile: DeviceProfile,
    onBack: () -> Unit,
    onClearCache: () -> Unit,
    onClearCookies: (onDone: () -> Unit) -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { firstFocus.requestFocusSafely() }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3_000)
            toast = null
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = TvSafeArea.horizontal, vertical = TvSafeArea.vertical)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvButton("Back", onClick = onBack, icon = Icons.AutoMirrored.Filled.ArrowBack)
                Spacer(Modifier.width(24.dp))
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp, start = 6.dp, end = 6.dp),
            ) {
                item { SectionLabel("Browsing") }
                item {
                    TvListRow(
                        title = "Default home page",
                        subtitle = settings.homePage.ifBlank { "Not set" },
                        onClick = { dialog = SettingsDialog.HOME_PAGE },
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                }
                item {
                    SwitchRow(
                        title = "Open home page at startup",
                        subtitle = if (settings.homePage.isBlank()) "Set a home page first" else null,
                        checked = settings.openHomePageOnStartup,
                        enabled = settings.homePage.isNotBlank(),
                        onToggle = { viewModel.setOpenHomePageOnStartup(!settings.openHomePageOnStartup) },
                    )
                }
                item {
                    SwitchRow(
                        title = "Show toolbar briefly when a page loads",
                        subtitle = "The full toolbar is always available with MENU or by holding BACK",
                        checked = settings.showToolbarOnPageLoad,
                        onToggle = { viewModel.setShowToolbarOnPageLoad(!settings.showToolbarOnPageLoad) },
                    )
                }
                item {
                    TvListRow(
                        title = "Remote navigation",
                        subtitle = navigationLabel(settings.navigationMode),
                        onClick = { dialog = SettingsDialog.NAVIGATION },
                    )
                }
                item {
                    TvListRow(
                        title = "Website text zoom",
                        subtitle = "${settings.textZoom}%",
                        onClick = { dialog = SettingsDialog.ZOOM },
                    )
                }
                item {
                    TvListRow(
                        title = "User agent",
                        subtitle = userAgentLabel(settings),
                        onClick = { dialog = SettingsDialog.USER_AGENT },
                    )
                }
                item {
                    SwitchRow(
                        title = "JavaScript",
                        subtitle = "Required by almost every video player. Applies to the next page load.",
                        checked = settings.javaScriptEnabled,
                        onToggle = { viewModel.setJavaScriptEnabled(!settings.javaScriptEnabled) },
                    )
                }

                item { SectionLabel("Appearance") }
                item {
                    SwitchRow(
                        title = "Dark mode",
                        subtitle = "Applies to the app screens. Websites use their own colours.",
                        checked = settings.darkMode,
                        onToggle = { viewModel.setDarkMode(!settings.darkMode) },
                    )
                }

                item { SectionLabel("Privacy & storage") }
                item {
                    TvListRow(
                        title = "Clear browsing history",
                        leadingIcon = Icons.Default.Delete,
                        onClick = { dialog = SettingsDialog.CLEAR_HISTORY },
                    )
                }
                item {
                    TvListRow(
                        title = "Clear cache",
                        subtitle = "Frees storage. Pages may load slower the next time.",
                        leadingIcon = Icons.Default.Delete,
                        onClick = { dialog = SettingsDialog.CLEAR_CACHE },
                    )
                }
                item {
                    TvListRow(
                        title = "Clear cookies and site data",
                        subtitle = "Signs you out of websites",
                        leadingIcon = Icons.Default.Delete,
                        onClick = { dialog = SettingsDialog.CLEAR_COOKIES },
                    )
                }

                item { SectionLabel("About") }
                item {
                    TvListRow(
                        title = "About Universal Stream TV",
                        subtitle = "Version ${BuildConfig.VERSION_NAME} · WebView ${deviceProfile.webViewVersion ?: "unknown"}",
                        leadingIcon = Icons.Default.Info,
                        onClick = { dialog = SettingsDialog.ABOUT },
                    )
                }
            }
        }

        toast?.let {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            ) {
                Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp))
            }
        }
    }

    val close: () -> Unit = { dialog = null }
    when (dialog) {
        SettingsDialog.HOME_PAGE -> TextInputDialog(
            title = "Default home page",
            initial = settings.homePage,
            placeholder = "example.com (leave empty to remove)",
            keyboardType = KeyboardType.Uri,
            validate = { text ->
                if (text.isBlank()) null
                else when (UrlUtils.parse(text)) {
                    is UrlParseResult.Valid -> null
                    is UrlParseResult.Invalid -> "Enter a valid web address, for example example.com"
                }
            },
            onSave = { text ->
                val url = (UrlUtils.parse(text) as? UrlParseResult.Valid)?.url ?: ""
                viewModel.setHomePage(url)
                if (url.isEmpty()) viewModel.setOpenHomePageOnStartup(false)
                close()
            },
            onDismiss = close,
        )
        SettingsDialog.NAVIGATION -> ChoiceDialog(
            title = "Remote navigation",
            options = NavigationMode.entries.map { navigationLabel(it) to navigationDescription(it) },
            selected = settings.navigationMode.ordinal,
            onSelect = { index ->
                viewModel.setNavigationMode(NavigationMode.entries[index])
                close()
            },
            onDismiss = close,
        )
        SettingsDialog.ZOOM -> ChoiceDialog(
            title = "Website text zoom",
            options = AppSettings.ZOOM_LEVELS.map { "$it%" to (if (it == 100) "Default" else null) },
            selected = AppSettings.ZOOM_LEVELS.indexOf(settings.textZoom).coerceAtLeast(0),
            onSelect = { index ->
                viewModel.setTextZoom(AppSettings.ZOOM_LEVELS[index])
                close()
            },
            onDismiss = close,
        )
        SettingsDialog.USER_AGENT -> ChoiceDialog(
            title = "User agent",
            options = listOf(
                "Default (Android TV WebView)" to "Safest choice. Some sites show a mobile layout.",
                "Desktop" to "Requests the full desktop site. Often better on a big screen.",
                "Mobile" to "Requests the phone version of sites.",
                "Custom…" to (settings.customUserAgent.ifBlank { null }),
            ),
            selected = settings.userAgentMode.ordinal,
            onSelect = { index ->
                val mode = UserAgentMode.entries[index]
                if (mode == UserAgentMode.CUSTOM) {
                    dialog = SettingsDialog.CUSTOM_UA
                } else {
                    viewModel.setUserAgent(mode)
                    close()
                }
            },
            onDismiss = close,
        )
        SettingsDialog.CUSTOM_UA -> TextInputDialog(
            title = "Custom user agent",
            initial = settings.customUserAgent,
            placeholder = "Mozilla/5.0 (...)",
            keyboardType = KeyboardType.Text,
            validate = { text -> if (text.isBlank()) "Enter a user agent string" else null },
            onSave = { text ->
                viewModel.setUserAgent(UserAgentMode.CUSTOM, text)
                close()
            },
            onDismiss = close,
        )
        SettingsDialog.CLEAR_HISTORY -> ConfirmDialog(
            title = "Clear browsing history?",
            message = "Your recently visited websites will be deleted. Favorites are kept.",
            confirmLabel = "Clear history",
            onConfirm = {
                viewModel.clearHistory()
                toast = "History cleared"
                close()
            },
            onDismiss = close,
        )
        SettingsDialog.CLEAR_CACHE -> ConfirmDialog(
            title = "Clear cache?",
            message = "Cached images, scripts and video segments will be removed.",
            confirmLabel = "Clear cache",
            onConfirm = {
                onClearCache()
                toast = "Cache cleared"
                close()
            },
            onDismiss = close,
        )
        SettingsDialog.CLEAR_COOKIES -> ConfirmDialog(
            title = "Clear cookies and site data?",
            message = "You will be signed out of all websites and their saved preferences will be removed.",
            confirmLabel = "Clear data",
            onConfirm = {
                onClearCookies { toast = "Cookies and site data cleared" }
                close()
            },
            onDismiss = close,
        )
        SettingsDialog.ABOUT -> AboutDialog(deviceProfile, onDismiss = close)
        null -> Unit
    }
}

private fun navigationLabel(mode: NavigationMode): String = when (mode) {
    NavigationMode.CURSOR -> "Pointer (recommended)"
    NavigationMode.DIRECT -> "Direct keys"
}

private fun navigationDescription(mode: NavigationMode): String = when (mode) {
    NavigationMode.CURSOR -> "The D-pad moves an on-screen pointer and OK clicks. Works on any website."
    NavigationMode.DIRECT -> "The D-pad goes straight to the website. Best for TV-optimised sites."
}

private fun userAgentLabel(settings: AppSettings): String = when (settings.userAgentMode) {
    UserAgentMode.DEFAULT -> "Default (Android TV WebView)"
    UserAgentMode.DESKTOP -> "Desktop"
    UserAgentMode.MOBILE -> "Mobile"
    UserAgentMode.CUSTOM -> "Custom: " + UrlUtils.ellipsize(settings.customUserAgent, 60)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    TvListRow(
        title = title,
        subtitle = subtitle,
        onClick = onToggle,
        enabled = enabled,
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocusSafely() }
    TvDialog(title = title, onDismiss = onDismiss) {
        Column(
            modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEachIndexed { index, (label, description) ->
                TvListRow(
                    title = label,
                    subtitle = description,
                    onClick = { onSelect(index) },
                    modifier = if (index == selected) Modifier.focusRequester(focus) else Modifier,
                    trailing = if (index == selected) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected", tint = LocalContentColor.current) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    keyboardType: KeyboardType,
    validate: (String) -> String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFocus.requestFocusSafely() }
    val save: () -> Unit = {
        val problem = validate(text)
        if (problem == null) {
            onSave(text.trim())
        } else {
            error = problem
        }
    }
    val currentError = error
    TvDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            TvButton("Cancel", onClick = onDismiss)
            TvButton("Save", onClick = save, primary = true)
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            singleLine = true,
            placeholder = { Text(placeholder) },
            isError = currentError != null,
            supportingText = if (currentError != null) {
                { Text(currentError) }
            } else {
                null
            },
            textStyle = MaterialTheme.typography.titleMedium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocusSafely() }
    TvDialog(
        title = title,
        onDismiss = onDismiss,
        icon = Icons.Default.Delete,
        buttons = {
            TvButton("Cancel", onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus))
            TvButton(confirmLabel, onClick = onConfirm, primary = true)
        },
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AboutDialog(profile: DeviceProfile, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocusSafely() }
    TvDialog(
        title = "About Universal Stream TV",
        onDismiss = onDismiss,
        icon = Icons.Default.Info,
        buttons = { TvButton("Close", onClick = onDismiss, primary = true, modifier = Modifier.focusRequester(focus)) },
    ) {
        Column(
            modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InfoLine("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoLine("Device", "${profile.manufacturer} ${profile.model}".trim())
            InfoLine("Android", "${profile.androidRelease} (API ${profile.sdkInt})")
            InfoLine("App resolution", "${profile.uiWidthPx} × ${profile.uiHeightPx} @ ${profile.densityDpi} dpi")
            InfoLine("Panel resolution", "${profile.panelWidthPx} × ${profile.panelHeightPx}" + if (profile.isUhdPanel) " (4K)" else "")
            InfoLine(
                "Memory",
                "${profile.totalRamMb} MB RAM · app limit ${profile.memoryClassMb} MB" + if (profile.isLowRamDevice) " · low-RAM device" else "",
            )
            InfoLine("CPU cores", profile.cpuCores.toString())
            InfoLine("Performance profile", if (profile.isLowPerformance) "Lightweight (low-memory optimisations on)" else "Standard")
            InfoLine("WebView", listOfNotNull(profile.webViewPackage, profile.webViewVersion).joinToString(" ").ifBlank { "Unknown" })
            if (profile.isOutdatedWebView) {
                Text(
                    "Your Android System WebView is old. Update it from the Play Store for better video support.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.width(1.dp))
            Text(
                "Universal Stream TV opens websites and plays videos with each site's own player. " +
                    "It does not download videos or bypass DRM, paywalls, sign-in, CAPTCHAs or certificate errors.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
