package com.universalstream.tv.ui.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalstream.tv.browser.BrowserController
import com.universalstream.tv.browser.BrowserError
import com.universalstream.tv.browser.BrowserErrorType
import com.universalstream.tv.browser.CursorState
import com.universalstream.tv.browser.JsDialogRequest
import com.universalstream.tv.browser.JsDialogType
import com.universalstream.tv.browser.PermissionPrompt
import com.universalstream.tv.browser.ToolbarMode
import com.universalstream.tv.browser.UiMessage
import com.universalstream.tv.core.UrlUtils
import com.universalstream.tv.ui.components.TvButton
import com.universalstream.tv.ui.components.TvDialog
import com.universalstream.tv.ui.components.TvIconButton
import com.universalstream.tv.ui.components.TvSurface
import com.universalstream.tv.ui.components.requestFocusSafely
import com.universalstream.tv.ui.theme.TvSafeArea
import kotlinx.coroutines.delay

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    controller: BrowserController,
    onHome: () -> Unit,
    onEditUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val cursor by controller.cursor.state.collectAsStateWithLifecycle()
    val overlayActive = state.toolbarMode == ToolbarMode.ACTIVE || state.error != null

    // While an overlay has focus the WebView must not steal D-pad focus back.
    LaunchedEffect(overlayActive, state.webViewGeneration) {
        controller.setContentFocusable(!overlayActive)
    }
    LaunchedEffect(state.toolbarMode, state.toolbarInteraction) {
        when (state.toolbarMode) {
            ToolbarMode.ACTIVE -> {
                delay(12_000)
                viewModel.setToolbarMode(ToolbarMode.HIDDEN)
            }
            ToolbarMode.PEEK -> {
                delay(3_500)
                viewModel.hidePeek()
            }
            ToolbarMode.HIDDEN -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        key(state.webViewGeneration) {
            AndroidView(
                factory = { controller.webViewForComposition() },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view -> controller.onCompositionReleased(view) },
            )
        }

        if (state.cursorEnabled && !state.isFullscreen && !overlayActive) {
            CursorOverlay(cursor)
        }

        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }

        when (state.toolbarMode) {
            ToolbarMode.ACTIVE -> BrowserToolbar(
                state = state,
                isFavorite = isFavorite,
                onBack = { controller.goBack() },
                onForward = controller::goForward,
                onReload = { if (state.isLoading) controller.stopLoading() else controller.reload() },
                onHome = onHome,
                onEditUrl = { onEditUrl(state.url) },
                onToggleFavorite = viewModel::toggleFavorite,
                onCopy = { onCopyUrl(state.url) },
                onToggleCursor = viewModel::toggleCursor,
                onExternal = { onOpenExternal(state.url) },
                onInteraction = viewModel::onToolbarInteraction,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            ToolbarMode.PEEK -> PeekBar(state, Modifier.align(Alignment.TopCenter))
            ToolbarMode.HIDDEN -> Unit
        }

        state.error?.let { error ->
            ErrorOverlay(
                error = error,
                canGoBack = state.canGoBack,
                onRetry = {
                    viewModel.clearError()
                    controller.retry()
                },
                onBack = {
                    viewModel.clearError()
                    if (!controller.goBack()) onHome()
                },
                onHome = {
                    viewModel.clearError()
                    onHome()
                },
            )
        }

        state.message?.let { message ->
            MessageBanner(message, onTimeout = viewModel::dismissMessage, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    state.jsDialog?.let { JsDialog(it, onResult = viewModel::resolveJsDialog) }
    state.permissionPrompt?.let { PermissionDialog(it, onResult = viewModel::resolvePermission) }
}

@Composable
private fun BrowserToolbar(
    state: BrowserUiState,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onEditUrl: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit,
    onToggleCursor: () -> Unit,
    onExternal: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusSafely() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.55f))))
            .padding(horizontal = TvSafeArea.horizontal, vertical = 18.dp)
            .onPreviewKeyEvent {
                onInteraction()
                false
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, enabled = state.canGoBack)
        TvIconButton(Icons.AutoMirrored.Filled.ArrowForward, "Forward", onForward, enabled = state.canGoForward)
        TvIconButton(
            if (state.isLoading) Icons.Default.Close else Icons.Default.Refresh,
            if (state.isLoading) "Stop" else "Reload",
            onReload,
            modifier = Modifier.focusRequester(firstFocus),
        )
        TvIconButton(Icons.Default.Home, "Home", onHome)
        TvSurface(
            onClick = onEditUrl,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = RoundedCornerShape(28.dp),
            focusScale = 1.02f,
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    if (state.isSecure) Icons.Default.Lock else Icons.Default.Warning,
                    contentDescription = if (state.isSecure) "Secure connection" else "Not secure",
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    state.url,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TvIconButton(
            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            if (isFavorite) "Unfavorite" else "Favorite",
            onToggleFavorite,
            active = isFavorite,
        )
        TvIconButton(Icons.Default.Share, "Copy URL", onCopy)
        TvIconButton(
            Icons.Default.LocationOn,
            if (state.cursorEnabled) "Pointer: on" else "Pointer: off",
            onToggleCursor,
            active = state.cursorEnabled,
        )
        TvIconButton(Icons.AutoMirrored.Filled.ExitToApp, "External browser", onExternal)
    }
}

@Composable
private fun PeekBar(state: BrowserUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .padding(horizontal = TvSafeArea.horizontal, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (state.isSecure) Icons.Default.Lock else Icons.Default.Warning,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                state.title.ifBlank { UrlUtils.hostOf(state.url) ?: state.url },
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.url,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "MENU or hold BACK: controls",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun CursorOverlay(state: CursorState) {
    if (!state.initialized) return
    val sizePx = with(LocalDensity.current) { 26.dp.toPx() }
    val strokePx = with(LocalDensity.current) { 2.dp.toPx() }
    Canvas(Modifier.fillMaxSize()) {
        val x = state.x
        val y = state.y
        val s = sizePx
        drawCircle(Color(0x556CC7FF), radius = s * 0.55f, center = Offset(x, y))
        val arrow = Path().apply {
            moveTo(x, y)
            lineTo(x, y + s)
            lineTo(x + s * 0.27f, y + s * 0.75f)
            lineTo(x + s * 0.47f, y + s * 1.12f)
            lineTo(x + s * 0.62f, y + s * 1.04f)
            lineTo(x + s * 0.43f, y + s * 0.68f)
            lineTo(x + s * 0.76f, y + s * 0.68f)
            close()
        }
        drawPath(arrow, Color.White)
        drawPath(arrow, Color.Black, style = Stroke(width = strokePx))
    }
}

@Composable
private fun ErrorOverlay(
    error: BrowserError,
    canGoBack: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(error) { retryFocus.requestFocusSafely() }
    val (title, explanation) = describeError(error)
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(explanation, style = MaterialTheme.typography.bodyLarge)
            error.detail?.takeIf { it.isNotBlank() && error.type != BrowserErrorType.SSL_ERROR }?.let {
                Text("Details: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (error.url.isNotBlank()) {
                Text(UrlUtils.ellipsize(error.url, 140), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton("Retry", onClick = onRetry, icon = Icons.Default.Refresh, primary = true, modifier = Modifier.focusRequester(retryFocus))
                TvButton(if (canGoBack) "Go back" else "Close", onClick = onBack, icon = Icons.AutoMirrored.Filled.ArrowBack)
                TvButton("Home", onClick = onHome, icon = Icons.Default.Home)
            }
        }
    }
}

private fun describeError(error: BrowserError): Pair<String, String> = when (error.type) {
    BrowserErrorType.NETWORK_UNAVAILABLE -> "No internet connection" to
        "Check the TV's Wi-Fi or Ethernet connection. This page reloads automatically when the connection is back."
    BrowserErrorType.DNS_FAILURE -> "Website not found" to
        "The website name could not be found. Check the address for typos, or the site may be offline."
    BrowserErrorType.TIMEOUT -> "The website took too long to respond" to
        "The site may be busy or your connection may be slow. Try again in a moment."
    BrowserErrorType.CONNECTION_FAILED -> "Couldn't connect to the website" to
        "The connection was refused or interrupted. The site may be down or blocked on this network."
    BrowserErrorType.SSL_ERROR -> "Connection not secure" to
        ((error.detail ?: "The website's security certificate is invalid.") +
            " For your safety the page was not opened. Certificate errors are never bypassed.")
    BrowserErrorType.UNSUPPORTED_SCHEME -> "Unsupported link" to
        "This link type can't be opened in Universal Stream TV."
    BrowserErrorType.BAD_URL -> "Invalid address" to "The web address is not valid."
    BrowserErrorType.TOO_MANY_REDIRECTS -> "Too many redirects" to
        "The website keeps redirecting and never finishes loading. Clearing cookies in Settings may help."
    BrowserErrorType.RENDERER_CRASHED -> "The page stopped working" to
        "The website's content process crashed (often low memory). Select Retry to load it again."
    BrowserErrorType.GENERIC -> "This page couldn't be loaded" to
        "Something went wrong while loading the website."
}

@Composable
private fun MessageBanner(message: UiMessage, onTimeout: (Long) -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message.id) {
        delay(4_500)
        onTimeout(message.id)
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier.padding(bottom = 36.dp, start = TvSafeArea.horizontal, end = TvSafeArea.horizontal),
    ) {
        Text(
            message.text,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun JsDialog(request: JsDialogRequest, onResult: (Boolean, String?) -> Unit) {
    val okFocus = remember { FocusRequester() }
    var text by remember(request) { mutableStateOf(request.defaultValue) }
    LaunchedEffect(request) { okFocus.requestFocusSafely() }
    val title = when (request.type) {
        JsDialogType.BEFORE_UNLOAD -> "Leave page?"
        else -> "${request.origin} says"
    }
    TvDialog(
        title = title,
        onDismiss = { onResult(false, null) },
        buttons = {
            if (request.type != JsDialogType.ALERT) {
                TvButton(
                    if (request.type == JsDialogType.BEFORE_UNLOAD) "Stay" else "Cancel",
                    onClick = { onResult(false, null) },
                )
            }
            TvButton(
                if (request.type == JsDialogType.BEFORE_UNLOAD) "Leave" else "OK",
                onClick = { onResult(true, if (request.type == JsDialogType.PROMPT) text else null) },
                primary = true,
                modifier = Modifier.focusRequester(okFocus),
            )
        },
    ) {
        Text(
            UrlUtils.ellipsize(request.message, 600),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.heightIn(max = 260.dp),
        )
        if (request.type == JsDialogType.PROMPT) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PermissionDialog(prompt: PermissionPrompt, onResult: (Boolean) -> Unit) {
    val denyFocus = remember { FocusRequester() }
    LaunchedEffect(prompt) { denyFocus.requestFocusSafely() }
    TvDialog(
        title = "Allow ${prompt.origin}?",
        onDismiss = { onResult(false) },
        buttons = {
            TvButton("Don't allow", onClick = { onResult(false) }, modifier = Modifier.focusRequester(denyFocus))
            TvButton("Allow", onClick = { onResult(true) }, primary = true)
        },
    ) {
        Text("This website wants to:", style = MaterialTheme.typography.bodyLarge)
        prompt.resourceLabels.forEach { label ->
            Text("• $label", style = MaterialTheme.typography.bodyLarge, color = LocalContentColor.current)
        }
        Text(
            "Allow only if you trust this site. The choice lasts until the app is closed or site data is cleared.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
