package com.visioo.app.feature.player

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visioo.app.R
import com.visioo.app.data.AccountRepository
import com.visioo.app.data.CatalogRepository
import com.visioo.app.data.ContentType
import com.visioo.app.data.PlayableSource
import com.visioo.app.data.PlaybackRepository
import com.visioo.app.ui.messageRes
import com.visioo.core.network.ApiException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Loading : PlayerUiState

    /** [needsVip] turns the error into a "activate a key" prompt instead of a retry. */
    data class Error(@StringRes val message: Int, val needsVip: Boolean = false) : PlayerUiState

    /**
     * [attempt] changes on every retry, so the screen re-prepares the player even when the source is the same.
     * [startMs] is where playback (re)starts: the resume point first, then wherever it broke.
     */
    data class Ready(
        val sources: List<PlayableSource>,
        val index: Int,
        val startMs: Long,
        val attempt: Int,
        val title: String?,
        val hasNextEpisode: Boolean,
        val autoplay: Boolean,
    ) : PlayerUiState
}

sealed interface PlayerEvent {
    data class PlayEpisode(val id: String) : PlayerEvent
}

class PlayerViewModel(
    private val playback: PlaybackRepository,
    private val catalog: CatalogRepository,
    private val account: AccountRepository,
    private val selectedProfileId: () -> String?,
    val kind: PlayKind,
    val id: String,
) : ViewModel() {
    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _events = Channel<PlayerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var policy = FallbackPolicy(0)
    private var nextEpisode: String? = null
    private var durationMs = 0L

    private val contentType get() = kind.path

    init { load() }

    fun load() {
        _state.value = PlayerUiState.Loading
        viewModelScope.launch {
            val extras = coroutineScope {
                val resolved = async { playback.resolve(contentType, id) }
                val position = async { playback.position(contentType, id) }
                val meta = async { loadMeta() }
                Triple(resolved.await(), position.await(), meta.await())
            }
            val (resolved, position, meta) = extras
            resolved.fold(
                onSuccess = { sources ->
                    policy = FallbackPolicy(sources.size)
                    nextEpisode = meta.nextEpisodeId
                    val saved = position.getOrNull()
                    _state.value = PlayerUiState.Ready(
                        sources = sources, index = 0,
                        startMs = saved?.let { ResumePolicy.startPosition(it.positionMs, it.durationMs) } ?: 0L,
                        attempt = 0, title = meta.title, hasNextEpisode = meta.nextEpisodeId != null, autoplay = meta.autoplay,
                    )
                },
                onFailure = { e ->
                    val code = (e as? ApiException)?.code
                    _state.value = PlayerUiState.Error(e.messageRes(), needsVip = code == "VIP_REQUIRED" || code == "VIP_EXPIRED")
                },
            )
        }
    }

    private data class Meta(val title: String?, val nextEpisodeId: String?, val autoplay: Boolean)

    private suspend fun loadMeta(): Meta {
        val autoplay = account.profiles().getOrNull()?.firstOrNull { it.id == selectedProfileId() }?.autoplay ?: true
        return when (kind) {
            PlayKind.Movie -> Meta(catalog.detail(ContentType.Movie, id).getOrNull()?.title, null, autoplay)
            PlayKind.Episode -> {
                val ep = playback.episode(id).getOrNull() ?: return Meta(null, null, autoplay)
                val seasons = catalog.seasons(ep.seriesId).getOrNull().orEmpty()
                Meta("${ep.seriesTitle} · S${ep.seasonNumber} E${ep.episodeNumber}", nextEpisodeId(seasons, id), autoplay)
            }
        }
    }

    /** Called by the screen when ExoPlayer fails. [positionMs] is where it broke, so recovery resumes there. */
    fun onPlayerError(authOrExpired: Boolean, positionMs: Long) {
        val current = _state.value as? PlayerUiState.Ready ?: return
        when (val action = policy.onError(authOrExpired)) {
            FallbackPolicy.Action.RetrySame -> _state.value = current.copy(startMs = positionMs, attempt = current.attempt + 1)
            is FallbackPolicy.Action.Next -> _state.value = current.copy(index = action.index, startMs = positionMs, attempt = current.attempt + 1)
            FallbackPolicy.Action.Refresh -> refresh(current, positionMs)
            FallbackPolicy.Action.GiveUp -> _state.value = PlayerUiState.Error(R.string.error_playback)
        }
    }

    private fun refresh(current: PlayerUiState.Ready, positionMs: Long) {
        viewModelScope.launch {
            playback.resolve(contentType, id).fold(
                onSuccess = { sources ->
                    policy = policy.onRefreshed(sources.size)
                    _state.value = current.copy(sources = sources, index = 0, startMs = positionMs, attempt = current.attempt + 1)
                },
                onFailure = { e ->
                    val code = (e as? ApiException)?.code
                    _state.value = PlayerUiState.Error(e.messageRes(), needsVip = code == "VIP_REQUIRED" || code == "VIP_EXPIRED")
                },
            )
        }
    }

    fun reportProgress(positionMs: Long, durationMs: Long) {
        if (durationMs > 0) this.durationMs = durationMs
        playback.saveProgress(contentType, id, positionMs, durationMs)
    }

    fun onEnded() {
        // A finished item is stored as completed, so it will not resume at the credits next time.
        if (durationMs > 0) playback.saveProgress(contentType, id, durationMs, durationMs)
        val ready = _state.value as? PlayerUiState.Ready ?: return
        val next = nextEpisode
        if (ready.autoplay && next != null) _events.trySend(PlayerEvent.PlayEpisode(next))
    }

    fun playNextNow() {
        nextEpisode?.let { _events.trySend(PlayerEvent.PlayEpisode(it)) }
    }
}
