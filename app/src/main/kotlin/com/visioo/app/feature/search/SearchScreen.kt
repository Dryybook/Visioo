package com.visioo.app.feature.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.visioo.app.R
import com.visioo.app.data.CatalogRepository
import com.visioo.app.data.ContentSummary
import com.visioo.app.ui.containerViewModel
import com.visioo.app.ui.messageRes
import com.visioo.core.designsystem.LoadingState
import com.visioo.core.designsystem.MessageState
import com.visioo.core.designsystem.PosterCard
import com.visioo.core.designsystem.VisiooSpacing
import com.visioo.core.designsystem.VisiooTextField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

const val SEARCH_DEBOUNCE_MS = 300L
const val MIN_QUERY = 2

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Results(val items: List<ContentSummary>) : SearchUiState
    data class Error(@StringRes val message: Int) : SearchUiState
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val repository: CatalogRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // collectLatest drops the in-flight request when the user keeps typing.
            query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged().collectLatest { q -> run(q.trim()) }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
        // Short input resets immediately, without waiting for the debounce.
        if (value.trim().length < MIN_QUERY) _state.value = SearchUiState.Idle
    }

    fun retry() = viewModelScope.launch { run(query.value.trim()) }

    private suspend fun run(q: String) {
        if (q.length < MIN_QUERY) {
            _state.value = SearchUiState.Idle
            return
        }
        _state.value = SearchUiState.Loading
        repository.search(q).fold(
            onSuccess = { _state.value = if (it.isEmpty()) SearchUiState.Empty else SearchUiState.Results(it) },
            onFailure = { _state.value = SearchUiState.Error(it.messageRes()) },
        )
    }
}

@Composable
fun SearchScreen(isTv: Boolean, onContentClick: (ContentSummary) -> Unit) {
    val vm = containerViewModel { SearchViewModel(it.catalogRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val edge = if (isTv) VisiooSpacing.Xxl else VisiooSpacing.Md

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md)) {
        VisiooTextField(
            text, { text = it; vm.onQueryChange(it) }, stringResource(R.string.search_hint),
            Modifier.fillMaxWidth().padding(horizontal = edge, vertical = VisiooSpacing.Md),
        )
        when (val s = state) {
            SearchUiState.Idle -> MessageState(stringResource(R.string.search_idle))
            SearchUiState.Loading -> LoadingState()
            SearchUiState.Empty -> MessageState(stringResource(R.string.search_empty))
            is SearchUiState.Error -> MessageState(stringResource(s.message), actionLabel = stringResource(R.string.action_retry), onAction = { vm.retry() })
            is SearchUiState.Results -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 180.dp else 110.dp),
                contentPadding = PaddingValues(horizontal = edge, vertical = VisiooSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md),
            ) {
                items(s.items, key = { "${it.type}-${it.id}" }) { item ->
                    PosterCard(item.title, item.posterUrl ?: item.backdropUrl, { onContentClick(item) }, isVip = item.isVip, cardWidth = if (isTv) 180.dp else 110.dp)
                }
            }
        }
    }
}
