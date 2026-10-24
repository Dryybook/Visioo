package com.visioo.app.feature.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visioo.app.data.session.SessionRepository
import com.visioo.app.ui.messageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(val loading: Boolean = false, @StringRes val error: Int? = null)

/** Drives login, registration, recovery and Google sign-in. Success is signalled by the session state, not here. */
class AuthViewModel(private val session: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun login(email: String, password: String) = run { session.login(email, password) }
    fun register(email: String, password: String, name: String) = run { session.register(email, password, name) }
    fun recover(email: String, phrase: String, newPassword: String) = run { session.recover(email, phrase, newPassword) }
    fun google(idToken: String) = run { session.google(idToken) }

    fun showError(@StringRes res: Int) = _state.update { it.copy(error = res) }
    fun clearError() = _state.update { it.copy(error = null) }

    private fun run(call: suspend () -> Result<Unit>) {
        if (_state.value.loading) return
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            val result = call()
            _state.value = AuthUiState(loading = false, error = result.exceptionOrNull()?.messageRes())
        }
    }
}
