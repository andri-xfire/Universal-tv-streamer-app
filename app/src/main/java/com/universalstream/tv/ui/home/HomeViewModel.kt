package com.universalstream.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalstream.tv.core.ShortenerRegistry
import com.universalstream.tv.core.UrlInvalidReason
import com.universalstream.tv.core.UrlParseResult
import com.universalstream.tv.core.UrlUtils
import com.universalstream.tv.data.AppSettings
import com.universalstream.tv.data.BrowserRepository
import com.universalstream.tv.data.FavoriteEntity
import com.universalstream.tv.data.HistoryEntity
import com.universalstream.tv.data.SettingsRepository
import com.universalstream.tv.resolver.ResolutionMessages
import com.universalstream.tv.resolver.ResolveOutcome
import com.universalstream.tv.resolver.UrlResolutionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ResolutionState {
    data object Idle : ResolutionState
    data class Resolving(val url: String) : ResolutionState
    data class Preview(val outcome: ResolveOutcome.Resolved) : ResolutionState
    data class Failed(val originalUrl: String, val message: String) : ResolutionState
}

data class HomeUiState(
    val input: String = "",
    val base: UrlBase = UrlBase.Manual,
    val inputError: String? = null,
    val inputHint: String? = null,
    val resolution: ResolutionState = ResolutionState.Idle,
)

class HomeViewModel(
    private val repository: BrowserRepository,
    private val resolver: UrlResolutionService,
    settingsRepository: SettingsRepository,
    historyRowSize: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val history: StateFlow<List<HistoryEntity>> = repository.recentHistory(historyRowSize)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<FavoriteEntity>> = repository.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val openChannel = Channel<String>(Channel.BUFFERED)

    /** URLs that are ready to be opened in the browser (already validated/resolved). */
    val openRequests: Flow<String> = openChannel.receiveAsFlow()

    private var resolveJob: Job? = null

    fun onInputChanged(text: String) {
        _state.update { it.copy(input = text, inputError = null, inputHint = hintFor(text, it.base)) }
    }

    /** Switches the base the typed value is combined with (Manual, shorturl.at, bit.ly, tinyurl.com). */
    fun onBaseChanged(base: UrlBase) {
        _state.update { it.copy(base = base, inputError = null, inputHint = hintFor(it.input, base)) }
    }

    fun setInput(text: String) = onInputChanged(text)

    fun clearInput() = onInputChanged("")

    fun onPasted(text: String?) {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) {
            _state.update { it.copy(inputError = "Papan klip kosong.") }
        } else {
            onInputChanged(clean)
        }
    }

    fun submit() {
        val current = _state.value
        val effective = current.base.combine(current.input)
        when (val parsed = UrlUtils.parse(effective)) {
            is UrlParseResult.Invalid -> _state.update { it.copy(inputError = describe(parsed)) }
            is UrlParseResult.Valid -> startResolution(parsed.url)
        }
    }

    private fun startResolution(url: String) {
        resolveJob?.cancel()
        _state.update { it.copy(resolution = ResolutionState.Resolving(url), inputError = null) }
        resolveJob = viewModelScope.launch {
            when (val outcome = resolver.resolve(url)) {
                is ResolveOutcome.Resolved -> {
                    if (outcome.requiresPreview) {
                        _state.update { it.copy(resolution = ResolutionState.Preview(outcome)) }
                    } else {
                        _state.update { it.copy(resolution = ResolutionState.Idle) }
                        openChannel.send(outcome.finalUrl)
                    }
                }
                is ResolveOutcome.Failed -> _state.update {
                    it.copy(resolution = ResolutionState.Failed(url, ResolutionMessages.describe(outcome.error)))
                }
            }
        }
    }

    fun cancelResolution() {
        resolveJob?.cancel()
        resolveJob = null
        _state.update { it.copy(resolution = ResolutionState.Idle) }
    }

    /** Opens a URL that needs no resolution (preview confirmed, history, favorites, home page). */
    fun open(url: String) {
        resolveJob?.cancel()
        _state.update { it.copy(resolution = ResolutionState.Idle) }
        viewModelScope.launch { openChannel.send(url) }
    }

    fun deleteHistory(url: String) {
        viewModelScope.launch { repository.deleteHistory(url) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun addFavorite(url: String, title: String) {
        viewModelScope.launch { repository.addFavorite(url, title) }
    }

    fun removeFavorite(url: String) {
        viewModelScope.launch { repository.removeFavorite(url) }
    }

    private fun hintFor(text: String, base: UrlBase): String? {
        if (base != UrlBase.Manual) {
            val code = text.trim()
            return if (code.isEmpty()) {
                "Ketik kode setelah ${base.label} — akan membuka ${base.prefix}…"
            } else {
                "Akan membuka ${base.combine(code)}"
            }
        }
        val parsed = UrlUtils.parse(text) as? UrlParseResult.Valid ?: return null
        return when {
            ShortenerRegistry.isKnownShortener(parsed.host) -> "Tautan pendek — tujuannya akan diperiksa sebelum dibuka"
            !parsed.isHttps -> "Tidak aman (HTTP): ${parsed.url}"
            parsed.schemeAdded -> "Akan membuka ${parsed.url}"
            else -> null
        }
    }

    private fun describe(invalid: UrlParseResult.Invalid): String = when (invalid.reason) {
        UrlInvalidReason.EMPTY -> "Masukkan alamat situs, misalnya example.com"
        UrlInvalidReason.CONTAINS_SPACES -> "Alamat web tidak boleh mengandung spasi."
        UrlInvalidReason.UNSUPPORTED_SCHEME -> "Tautan \"${invalid.scheme}:\" tidak didukung. Gunakan alamat http atau https."
        UrlInvalidReason.MALFORMED -> "Ini sepertinya bukan alamat web yang valid."
        UrlInvalidReason.INVALID_HOST -> "Nama situs tidak valid. Contoh: example.com"
        UrlInvalidReason.INVALID_PORT -> "Nomor port tidak valid."
    }
}
