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
        _state.update { it.copy(input = text, inputError = null, inputHint = hintFor(text)) }
    }

    fun setInput(text: String) = onInputChanged(text)

    fun clearInput() = onInputChanged("")

    fun onPasted(text: String?) {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) {
            _state.update { it.copy(inputError = "The clipboard is empty.") }
        } else {
            onInputChanged(clean)
        }
    }

    fun submit() {
        when (val parsed = UrlUtils.parse(_state.value.input)) {
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

    private fun hintFor(text: String): String? {
        val parsed = UrlUtils.parse(text) as? UrlParseResult.Valid ?: return null
        return when {
            ShortenerRegistry.isKnownShortener(parsed.host) -> "Short link — the destination will be checked before opening"
            !parsed.isHttps -> "Not secure (HTTP): ${parsed.url}"
            parsed.schemeAdded -> "Will open ${parsed.url}"
            else -> null
        }
    }

    private fun describe(invalid: UrlParseResult.Invalid): String = when (invalid.reason) {
        UrlInvalidReason.EMPTY -> "Enter a website address, for example example.com"
        UrlInvalidReason.CONTAINS_SPACES -> "Web addresses can't contain spaces."
        UrlInvalidReason.UNSUPPORTED_SCHEME -> "\"${invalid.scheme}:\" links are not supported. Use an http or https address."
        UrlInvalidReason.MALFORMED -> "This doesn't look like a valid web address."
        UrlInvalidReason.INVALID_HOST -> "The website name is not valid. Example: example.com"
        UrlInvalidReason.INVALID_PORT -> "The port number is not valid."
    }
}
