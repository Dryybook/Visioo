package com.visioo.app.feature.details

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.visioo.core.designsystem.VisiooImage
import com.visioo.app.R
import com.visioo.app.data.CatalogRepository
import com.visioo.app.data.ContentDetail
import com.visioo.app.data.ContentType
import com.visioo.app.data.Episode
import com.visioo.app.data.Season
import com.visioo.app.ui.containerViewModel
import com.visioo.app.ui.messageRes
import com.visioo.core.designsystem.LoadingState
import com.visioo.core.designsystem.MessageState
import com.visioo.core.designsystem.VisiooButton
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooShapes
import com.visioo.core.designsystem.VisiooSpacing
import com.visioo.core.designsystem.visiooFocus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Error(@StringRes val message: Int) : DetailsUiState
    data class Ready(
        val detail: ContentDetail,
        val seasons: List<Season> = emptyList(),
        val selectedSeason: Int = 0,
        @StringRes val seasonsError: Int? = null,
    ) : DetailsUiState
}

class DetailsViewModel(private val repository: CatalogRepository, private val type: ContentType, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = DetailsUiState.Loading
        viewModelScope.launch {
            repository.detail(type, id).fold(
                onSuccess = { detail ->
                    _state.value = DetailsUiState.Ready(detail)
                    if (type == ContentType.Series) loadSeasons()
                },
                onFailure = { _state.value = DetailsUiState.Error(it.messageRes()) },
            )
        }
    }

    private suspend fun loadSeasons() {
        repository.seasons(id).fold(
            onSuccess = { seasons -> _state.update { if (it is DetailsUiState.Ready) it.copy(seasons = seasons, seasonsError = null) else it } },
            onFailure = { e -> _state.update { if (it is DetailsUiState.Ready) it.copy(seasonsError = e.messageRes()) else it } },
        )
    }

    fun selectSeason(index: Int) = _state.update { if (it is DetailsUiState.Ready) it.copy(selectedSeason = index) else it }
}

@Composable
fun DetailsScreen(
    type: ContentType,
    id: String,
    isTv: Boolean,
    onPlayMovie: (String) -> Unit,
    onPlayEpisode: (String) -> Unit,
    onActivateVip: () -> Unit,
    onBack: () -> Unit,
) {
    val vm = containerViewModel(key = "details-${type.path}-$id") { DetailsViewModel(it.catalogRepository, type, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val edge = if (isTv) VisiooSpacing.Xxl else VisiooSpacing.Md

    when (val s = state) {
        DetailsUiState.Loading -> LoadingState()
        is DetailsUiState.Error -> MessageState(stringResource(s.message), actionLabel = stringResource(R.string.action_retry), onAction = vm::load)
        is DetailsUiState.Ready -> {
            val d = s.detail
            LazyColumn(contentPadding = PaddingValues(bottom = VisiooSpacing.Xl)) {
                item {
                    Box(Modifier.fillMaxWidth().height(if (isTv) 420.dp else 300.dp).background(VisiooColors.Surface)) {
                        (d.backdropUrl ?: d.posterUrl)?.let {
                            VisiooImage(it, contentDescription = null, modifier = Modifier.matchParentSize())
                        }
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(VisiooColors.Black.copy(alpha = 0.1f), VisiooColors.Black))))
                        VisiooButton(stringResource(R.string.action_back), onBack, Modifier.padding(VisiooSpacing.Md), primary = false)
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = edge), verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm)) {
                        Text(d.title, style = MaterialTheme.typography.displaySmall, color = VisiooColors.TextPrimary)
                        Text(metaLine(d), style = MaterialTheme.typography.labelMedium, color = VisiooColors.TextSecondary)
                        if (d.genres.isNotEmpty()) {
                            Text(d.genres.joinToString(" · ") { it.name }, style = MaterialTheme.typography.labelMedium, color = VisiooColors.VioletSoft)
                        }
                        d.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = VisiooColors.TextSecondary, modifier = Modifier.widthIn(max = 640.dp)) }
                        // Rights are decided by the server (`canPlay`); the app never infers VIP state itself.
                        val firstEpisode = s.seasons.firstOrNull { it.episodes.isNotEmpty() }?.episodes?.first()
                        val watch: (() -> Unit)? = if (d.type == ContentType.Movie) ({ onPlayMovie(d.id) }) else firstEpisode?.let { ep -> { onPlayEpisode(ep.id) } }
                        if (d.canPlay) { if (watch != null) VisiooButton(stringResource(R.string.action_watch), watch, Modifier.padding(top = VisiooSpacing.Sm)) }
                        else VisiooButton(stringResource(R.string.details_vip_required), onActivateVip, Modifier.padding(top = VisiooSpacing.Sm))
                    }
                }
                if (d.type == ContentType.Series) seasonsSection(s, edge, onSelect = vm::selectSeason, onEpisode = { onPlayEpisode(it.id) }, canPlay = d.canPlay)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.seasonsSection(
    s: DetailsUiState.Ready,
    edge: androidx.compose.ui.unit.Dp,
    onSelect: (Int) -> Unit,
    onEpisode: (Episode) -> Unit,
    canPlay: Boolean,
) {
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = edge, vertical = VisiooSpacing.Md), horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm)) {
            items(s.seasons.size) { i ->
                val season = s.seasons[i]
                VisiooButton(season.title ?: stringResource(R.string.details_season_n, season.number), { onSelect(i) }, primary = i == s.selectedSeason)
            }
        }
    }
    val season = s.seasons.getOrNull(s.selectedSeason)
    when {
        s.seasonsError != null -> item { Text(stringResource(s.seasonsError), color = VisiooColors.Error, modifier = Modifier.padding(horizontal = edge)) }
        season == null || season.episodes.isEmpty() -> item { Text(stringResource(R.string.details_episodes_empty), color = VisiooColors.TextMuted, modifier = Modifier.padding(horizontal = edge)) }
        else -> items(season.episodes, key = { it.id }) { ep -> EpisodeRow(ep, edge, enabled = canPlay) { onEpisode(ep) } }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, edge: androidx.compose.ui.unit.Dp, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = edge, vertical = VisiooSpacing.Sm)
            .visiooFocus(VisiooShapes.Card)
            .clip(VisiooShapes.Card)
            .background(VisiooColors.Surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(VisiooSpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 120.dp, height = 68.dp).clip(VisiooShapes.Card).background(VisiooColors.SurfaceElevated)) {
            ep.thumbnailUrl?.let { VisiooImage(it, contentDescription = null, modifier = Modifier.matchParentSize()) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.details_episode_n, ep.number, ep.title), style = MaterialTheme.typography.titleMedium, color = VisiooColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ep.runtimeMin?.let { Text(stringResource(R.string.details_minutes, it), style = MaterialTheme.typography.labelMedium, color = VisiooColors.TextMuted) }
        }
    }
}

@Composable
private fun metaLine(d: ContentDetail): String = listOfNotNull(
    if (d.isVip) stringResource(R.string.details_vip_badge) else null,
    d.year?.toString(),
    d.runtimeMin?.let { stringResource(R.string.details_minutes, it) },
    d.rating?.let { "★ %.1f".format(it) },
    if (d.ageRating > 0) stringResource(R.string.details_age, d.ageRating) else null,
).joinToString(" · ")
