package com.universalstream.tv.ui.home

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalstream.tv.core.UrlParseResult
import com.universalstream.tv.core.UrlUtils
import com.universalstream.tv.resolver.ResolveOutcome
import com.universalstream.tv.ui.browser.BrowserUiState
import com.universalstream.tv.ui.components.TvButton
import com.universalstream.tv.ui.components.TvDialog
import com.universalstream.tv.ui.components.TvSurface
import com.universalstream.tv.ui.components.requestFocusSafely
import com.universalstream.tv.ui.theme.TvGradients
import com.universalstream.tv.ui.theme.TvSafeArea
import kotlinx.coroutines.delay

/** A site shown as a card on the Home screen. */
data class SiteItem(
    val url: String,
    val title: String,
    val isFavorite: Boolean,
    val inHistory: Boolean,
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    session: BrowserUiState?,
    onContinueSession: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitApp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val urlFocus = remember { FocusRequester() }
    var optionsFor by remember { mutableStateOf<SiteItem?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var lastBackPress by remember { mutableLongStateOf(0L) }
    var showExitHint by remember { mutableStateOf(false) }

    // BACK on Home never exits by accident: a second press within 2 seconds is required.
    BackHandler {
        val now = SystemClock.uptimeMillis()
        if (now - lastBackPress < 2_000) {
            onExitApp()
        } else {
            lastBackPress = now
            showExitHint = true
        }
    }
    LaunchedEffect(showExitHint) {
        if (showExitHint) {
            delay(2_000)
            showExitHint = false
        }
    }
    LaunchedEffect(Unit) { urlFocus.requestFocusSafely() }

    val favoriteUrls = remember(favorites) { favorites.map { it.url }.toSet() }
    val historyUrls = remember(history) { history.map { it.url }.toSet() }
    val homePageUrl = (UrlUtils.parse(settings.homePage) as? UrlParseResult.Valid)?.url

    Box(Modifier.fillMaxSize().background(TvGradients.appBackground())) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = TvSafeArea.horizontal, vertical = TvSafeArea.vertical),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TvGradients.accent()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Universal Stream TV", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Streaming web untuk TV-mu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                TvButton("Pengaturan", onClick = onOpenSettings, icon = Icons.Default.Settings)
            }

            Spacer(Modifier.height(20.dp))

            // "Hero" panel: groups the search controls so they feel like one soft card.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(TvGradients.heroPanel())
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        RoundedCornerShape(28.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    "Buka situs atau tempel tautan pendek",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
                )

                BaseSelector(
                    selected = state.base,
                    onSelect = viewModel::onBaseChanged,
                )

                Spacer(Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    UrlInputField(
                        value = state.input,
                        onValueChange = viewModel::onInputChanged,
                        onSubmit = viewModel::submit,
                        modifier = Modifier.weight(1f),
                        focusRequester = urlFocus,
                        placeholder = if (state.base == UrlBase.Manual) {
                            "Ketik alamat situs atau tautan pendek, mis. bit.ly/abc"
                        } else {
                            "Ketik kode setelah ${state.base.label} mis. abc123"
                        },
                    )
                    TvButton("Tempel", onClick = { viewModel.onPasted(clipboard.getText()?.text) })
                    TvButton("Hapus", onClick = viewModel::clearInput, icon = Icons.Default.Clear)
                    TvButton("Buka", onClick = viewModel::submit, icon = Icons.Default.PlayArrow, containerBrush = TvGradients.accent())
                }

                Box(Modifier.heightIn(min = 30.dp).padding(start = 4.dp, top = 8.dp)) {
                    val error = state.inputError
                    val hint = state.inputHint
                    when {
                        error != null -> Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        hint != null -> Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (session != null && session.hasSession) {
                    item(key = "continue") {
                        SiteSection(title = "Lanjutkan") {
                            LazyRow(contentPadding = CardRowPadding, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                item {
                                    SiteCard(
                                        title = session.title.ifBlank { UrlUtils.hostOf(session.url) ?: session.url },
                                        url = session.url,
                                        icon = Icons.Default.PlayArrow,
                                        onClick = onContinueSession,
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "favorites") {
                    SiteSection(title = "Favorit") {
                        if (favorites.isEmpty() && homePageUrl == null) {
                            EmptyHint("Belum ada favorit. Buka sebuah situs, tekan MENU (atau tahan BACK), lalu pilih Favorit.")
                        } else {
                            LazyRow(contentPadding = CardRowPadding, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                if (homePageUrl != null) {
                                    item(key = "home:$homePageUrl") {
                                        SiteCard(
                                            title = "Beranda",
                                            url = homePageUrl,
                                            icon = Icons.Default.Home,
                                            onClick = { viewModel.open(homePageUrl) },
                                        )
                                    }
                                }
                                items(favorites, key = { "fav:" + it.url }) { fav ->
                                    val item = SiteItem(fav.url, fav.title, isFavorite = true, inHistory = fav.url in historyUrls)
                                    SiteCard(
                                        title = fav.title.ifBlank { UrlUtils.hostOf(fav.url) ?: fav.url },
                                        url = fav.url,
                                        icon = Icons.Default.Star,
                                        onClick = { viewModel.open(fav.url) },
                                        onLongClick = { optionsFor = item },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "history") {
                    SiteSection(
                        title = "Baru dikunjungi",
                        action = if (history.isNotEmpty()) {
                            { TvButton("Hapus semua", onClick = { confirmClearHistory = true }, icon = Icons.Default.Delete) }
                        } else {
                            null
                        },
                    ) {
                        if (history.isEmpty()) {
                            EmptyHint("Situs yang kamu buka akan muncul di sini.")
                        } else {
                            LazyRow(contentPadding = CardRowPadding, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                items(history, key = { "hist:" + it.url }) { entry ->
                                    val item = SiteItem(entry.url, entry.title, isFavorite = entry.url in favoriteUrls, inHistory = true)
                                    SiteCard(
                                        title = entry.title.ifBlank { UrlUtils.hostOf(entry.url) ?: entry.url },
                                        url = entry.url,
                                        icon = if (item.isFavorite) Icons.Default.Star else Icons.AutoMirrored.Filled.List,
                                        onClick = { viewModel.open(entry.url) },
                                        onLongClick = { optionsFor = item },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "tip") {
                    Text(
                        "Tips: tahan OK atau tekan MENU pada kartu untuk opsi lainnya.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        if (showExitHint) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            ) {
                Text("Tekan BACK sekali lagi untuk keluar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp))
            }
        }
    }

    ResolutionDialogs(
        resolution = state.resolution,
        onCancel = viewModel::cancelResolution,
        onOpen = viewModel::open,
    )

    optionsFor?.let { item ->
        SiteOptionsDialog(
            item = item,
            onDismiss = { optionsFor = null },
            onOpen = {
                optionsFor = null
                viewModel.open(item.url)
            },
            onToggleFavorite = {
                optionsFor = null
                if (item.isFavorite) viewModel.removeFavorite(item.url) else viewModel.addFavorite(item.url, item.title)
            },
            onDeleteHistory = {
                optionsFor = null
                viewModel.deleteHistory(item.url)
            },
        )
    }

    if (confirmClearHistory) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocusSafely() }
        TvDialog(
            title = "Hapus semua riwayat?",
            onDismiss = { confirmClearHistory = false },
            icon = Icons.Default.Delete,
            buttons = {
                TvButton("Batal", onClick = { confirmClearHistory = false }, modifier = Modifier.focusRequester(focus))
                TvButton("Hapus riwayat", onClick = {
                    confirmClearHistory = false
                    viewModel.clearHistory()
                }, primary = true)
            },
        ) {
            Text("Daftar situs yang baru kamu kunjungi akan dihapus. Favorit tetap tersimpan.", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * A D-pad friendly row of chips that choose what the URL box is combined with:
 * Manual (type the whole address) or one of the short-link bases. The selected chip is highlighted.
 */
@Composable
private fun BaseSelector(
    selected: UrlBase,
    onSelect: (UrlBase) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Basis URL:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 2.dp),
        )
        UrlBase.values().forEach { base ->
            val isSelected = base == selected
            TvSurface(
                onClick = { onSelect(base) },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                containerBrush = if (isSelected) TvGradients.accent() else null,
                focusScale = 1.04f,
            ) {
                Text(
                    base.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private val CardRowPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)

@Composable
private fun SiteSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun SiteCard(
    title: String,
    url: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    TvSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.size(width = 236.dp, height = 124.dp),
        containerBrush = TvGradients.card(),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    UrlUtils.hostOf(url) ?: url,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SiteOptionsDialog(
    item: SiteItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteHistory: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocusSafely() }
    TvDialog(title = item.title.ifBlank { UrlUtils.hostOf(item.url) ?: item.url }, onDismiss = onDismiss) {
        Text(UrlUtils.ellipsize(item.url, 140), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Buka", onClick = onOpen, icon = Icons.Default.PlayArrow, primary = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
            TvButton(
                if (item.isFavorite) "Hapus dari favorit" else "Tambah ke favorit",
                onClick = onToggleFavorite,
                icon = Icons.Default.Star,
                modifier = Modifier.fillMaxWidth(),
            )
            if (item.inHistory) {
                TvButton("Hapus dari riwayat", onClick = onDeleteHistory, icon = Icons.Default.Delete, modifier = Modifier.fillMaxWidth())
            }
            TvButton("Batal", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ResolutionDialogs(
    resolution: ResolutionState,
    onCancel: () -> Unit,
    onOpen: (String) -> Unit,
) {
    when (resolution) {
        ResolutionState.Idle -> Unit
        is ResolutionState.Resolving -> {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocusSafely() }
            TvDialog(
                title = "Memeriksa tautan…",
                onDismiss = onCancel,
                buttons = { TvButton("Batal", onClick = onCancel, modifier = Modifier.focusRequester(focus)) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    Column {
                        Text(UrlUtils.ellipsize(resolution.url, 90), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Mengikuti pengalihan untuk menemukan tujuan sebenarnya.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        is ResolutionState.Preview -> PreviewDialog(resolution.outcome, onCancel, onOpen)
        is ResolutionState.Failed -> {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocusSafely() }
            TvDialog(
                title = "Tidak bisa membuka tautan",
                onDismiss = onCancel,
                icon = Icons.Default.Warning,
                buttons = {
                    TvButton("Batal", onClick = onCancel)
                    TvButton(
                        "Buka tautan asli",
                        onClick = { onOpen(resolution.originalUrl) },
                        primary = true,
                        modifier = Modifier.focusRequester(focus),
                    )
                },
            ) {
                Text(resolution.message, style = MaterialTheme.typography.bodyLarge)
                LabeledUrl("Tautan", resolution.originalUrl)
                Text(
                    "Kamu tetap bisa membuka tautan aslinya; situs akan menangani pengalihannya sendiri.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewDialog(
    outcome: ResolveOutcome.Resolved,
    onCancel: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val openFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    // A secure-to-insecure downgrade is suspicious: default focus goes to Cancel.
    LaunchedEffect(Unit) { (if (outcome.downgradedToHttp) cancelFocus else openFocus).requestFocusSafely() }
    TvDialog(
        title = "Buka situs ini?",
        onDismiss = onCancel,
        buttons = {
            TvButton("Batal", onClick = onCancel, modifier = Modifier.focusRequester(cancelFocus))
            TvButton(
                "Buka",
                onClick = { onOpen(outcome.finalUrl) },
                icon = Icons.Default.PlayArrow,
                primary = true,
                modifier = Modifier.focusRequester(openFocus),
            )
        },
    ) {
        LabeledUrl("Tautan asli", outcome.originalUrl)
        LabeledUrl("Tujuan", outcome.finalUrl, emphasize = true)
        val details = buildString {
            append(if (outcome.redirectCount == 1) "1 pengalihan" else "${outcome.redirectCount} pengalihan")
            if (outcome.usedBrowserFallback) append(" · tujuan ditemukan dengan memuat halaman tautan")
        }
        Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (outcome.downgradedToHttp) {
            WarningText("Tautan ini beralih dari alamat aman (HTTPS) ke alamat yang tidak aman. Lanjutkan hanya jika kamu mempercayainya.")
        } else if (outcome.isInsecure) {
            WarningText("Situs ini tidak menggunakan HTTPS. Apa pun yang kamu masukkan di sana bisa dilihat orang lain di jaringan.")
        }
    }
}

@Composable
private fun LabeledUrl(label: String, url: String, emphasize: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            UrlUtils.ellipsize(url, 160),
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WarningText(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}
