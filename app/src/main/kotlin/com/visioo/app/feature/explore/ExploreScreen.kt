package com.visioo.app.feature.explore

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.visioo.app.R
import com.visioo.app.data.CatalogRepository
import com.visioo.app.data.ContentSummary
import com.visioo.app.data.ContentType
import com.visioo.app.data.Genre
import com.visioo.app.ui.containerViewModel
import com.visioo.app.ui.messageRes
import com.visioo.core.designsystem.LoadingState
import com.visioo.core.designsystem.MessageState
import com.visioo.core.designsystem.PosterCard
import com.visioo.core.designsystem.VisiooButton
import com.visioo.core.designsystem.VisiooSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

const val PAGE_SIZE = 20

data class ExploreUiState(
    val type: ContentType,
    val genres: List<Genre> = emptyList(),
    val genre: String? = null,
    val items: List<ContentSummary> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    @StringRes val error: Int? = null,
)

class ExploreViewModel(private val repository: CatalogRepository, initialType: ContentType) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState(type = initialType))
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { repository.genres().onSuccess { g -> _state.value = _state.value.copy(genres = g) } }
        reload()
    }

    fun selectType(type: ContentType) {
        if (type == _state.value.type) return
        _state.value = _state.value.copy(type = type, genre = null)
        reload()
    }

    fun selectGenre(slug: String?) {
        if (slug == _state.value.genre) return
        _state.value = _state.value.copy(genre = slug)
        reload()
    }

    fun retry() = if (_state.value.items.isEmpty()) reload() else loadMore()

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached || job?.isActive == true) return
        fetch(append = true)
    }

    private fun reload() {
        job?.cancel()
        _state.value = _state.value.copy(items = emptyList(), loading = true, loadingMore = false, endReached = false, error = null)
        fetch(append = false)
    }

    private fun fetch(append: Boolean) {
        val s = _state.value
        if (append) _state.value = s.copy(loadingMore = true, error = null)
        // A new type/genre cancels this job, so a slow old response can never overwrite the new list.
        job = viewModelScope.launch {
            repository.browse(s.type, s.genre, offset = if (append) s.items.size else 0, limit = PAGE_SIZE).fold(
                onSuccess = { page ->
                    val cur = _state.value
                    _state.value = cur.copy(
                        items = if (append) (cur.items + page).distinctBy { it.id } else page,
                        loading = false, loadingMore = false, endReached = page.size < PAGE_SIZE,
                    )
                },
                onFailure = { _state.value = _state.value.copy(loading = false, loadingMore = false, error = it.messageRes()) },
            )
        }
    }
}

@Composable
fun ExploreScreen(initialType: ContentType, isTv: Boolean, onContentClick: (ContentSummary) -> Unit) {
    val vm = containerViewModel(key = "explore-${initialType.path}") { ExploreViewModel(it.catalogRepository, initialType) }
    val state by vm.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val edge = if (isTv) VisiooSpacing.Xxl else VisiooSpacing.Md

    LaunchedEffect(gridState, state.items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= state.items.size - 6 }
            .collect { vm.loadMore() }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md)) {
        Row(Modifier.padding(horizontal = edge, vertical = VisiooSpacing.Md), horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm)) {
            for (type in ContentType.entries) {
                Chip(stringResource(if (type == ContentType.Movie) R.string.nav_movies else R.string.nav_series), state.type == type) { vm.selectType(type) }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = edge), horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm)) {
            item(key = "all") { Chip(stringResource(R.string.explore_all), state.genre == null) { vm.selectGenre(null) } }
            items(state.genres, key = { it.slug }) { g -> Chip(g.name, state.genre == g.slug) { vm.selectGenre(g.slug) } }
        }
        when {
            state.loading -> LoadingState()
            state.items.isEmpty() && state.error != null ->
                MessageState(stringResource(state.error!!), actionLabel = stringResource(R.string.action_retry), onAction = vm::retry)
            state.items.isEmpty() -> MessageState(stringResource(R.string.home_empty))
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(if (isTv) 180.dp else 110.dp),
                contentPadding = PaddingValues(horizontal = edge, vertical = VisiooSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md),
            ) {
                items(state.items, key = { it.id }) { item ->
                    PosterCard(item.title, item.posterUrl ?: item.backdropUrl, { onContentClick(item) }, isVip = item.isVip, cardWidth = if (isTv) 180.dp else 110.dp)
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    VisiooButton(label, onClick, primary = selected)
}
