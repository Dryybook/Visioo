package com.visioo.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visioo.app.data.CatalogRepository
import com.visioo.app.data.HomeContent
import com.visioo.core.network.ApiError
import com.visioo.core.network.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Success(val content: HomeContent) : HomeUiState
    data class Error(val error: ApiError, val code: String? = null) : HomeUiState
}

class HomeViewModel(private val repository: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            repository.getHome().fold(
                onSuccess = { content ->
                    _state.value = if (content.hero.isEmpty() && content.sections.isEmpty()) HomeUiState.Empty else HomeUiState.Success(content)
                },
                onFailure = {
                    val e = it as? ApiException
                    _state.value = HomeUiState.Error(e?.error ?: ApiError.Unknown, e?.code)
                },
            )
        }
    }
}
