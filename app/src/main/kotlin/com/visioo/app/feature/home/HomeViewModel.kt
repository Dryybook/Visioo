package com.visioo.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.visioo.app.VisiooApplication
import com.visioo.app.data.HomeRepository
import com.visioo.app.data.HomeSection
import com.visioo.core.network.ApiError
import com.visioo.core.network.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Success(val sections: List<HomeSection>) : HomeUiState
    data class Error(val error: ApiError) : HomeUiState
}

class HomeViewModel(private val repository: HomeRepository) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            repository.getHome().fold(
                onSuccess = { sections ->
                    _state.value = if (sections.isEmpty()) HomeUiState.Empty else HomeUiState.Success(sections)
                },
                onFailure = { _state.value = HomeUiState.Error((it as? ApiException)?.error ?: ApiError.Unknown) },
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VisiooApplication
                HomeViewModel(app.container.homeRepository)
            }
        }
    }
}
