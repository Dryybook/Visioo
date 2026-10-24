package com.visioo.app.data.session

import com.visioo.core.network.DeviceInfoDto
import com.visioo.core.network.GoogleRequest
import com.visioo.core.network.LoginRequest
import com.visioo.core.network.RecoverRequest
import com.visioo.core.network.RefreshRequest
import com.visioo.core.network.RegisterRequest
import com.visioo.core.network.SessionDto
import com.visioo.core.network.SessionStore
import com.visioo.core.network.VisiooApi
import com.visioo.core.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface SessionState {
    data object LoggedOut : SessionState

    /**
     * @param setupPending true right after sign-up: recovery phrase and VIP key steps come before the profile picker.
     * @param profileSelected false until the user picks a profile on this device.
     */
    data class LoggedIn(val setupPending: Boolean, val profileSelected: Boolean) : SessionState
}

class SessionRepository(
    private val api: VisiooApi,
    private val store: SessionStore,
    private val device: () -> DeviceInfoDto,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private fun initialState(): SessionState =
        if (store.refreshToken() != null) SessionState.LoggedIn(setupPending = false, profileSelected = store.profileId() != null)
        else SessionState.LoggedOut

    /** Called by the network layer when the server rejects the refresh token. */
    fun onSessionExpired() {
        _state.value = SessionState.LoggedOut
    }

    suspend fun login(email: String, password: String): Result<Unit> =
        safeApiCall { api.login(LoginRequest(email.trim(), password, device())) }.onSession(setupPending = false)

    suspend fun register(email: String, password: String, profileName: String?): Result<Unit> =
        safeApiCall { api.register(RegisterRequest(email.trim(), password, profileName?.trim()?.ifBlank { null }, device())) }
            .onSession(setupPending = true)

    suspend fun google(idToken: String): Result<Unit> =
        safeApiCall { api.google(GoogleRequest(idToken, device())) }.onSession(setupPending = false)

    /** After a successful recovery the old phrase is burned, so the user is sent to create a new one. */
    suspend fun recover(email: String, phrase: String, newPassword: String): Result<Unit> =
        safeApiCall { api.recover(RecoverRequest(email.trim(), phrase, newPassword, device())) }.onSession(setupPending = true)

    fun completeSetup() = _state.update { if (it is SessionState.LoggedIn) it.copy(setupPending = false) else it }

    fun selectProfile(id: String) {
        store.setProfileId(id)
        _state.update { if (it is SessionState.LoggedIn) it.copy(profileSelected = true) else it }
    }

    fun clearProfile() {
        store.setProfileId(null)
        _state.update { if (it is SessionState.LoggedIn) it.copy(profileSelected = false) else it }
    }

    fun selectedProfileId(): String? = store.profileId()

    suspend fun logout() {
        store.refreshToken()?.let { token -> safeApiCall { api.logout(RefreshRequest(token)) } }
        store.clear()
        _state.value = SessionState.LoggedOut
    }

    private fun Result<SessionDto>.onSession(setupPending: Boolean): Result<Unit> = map { session ->
        store.saveTokens(session.accessToken, session.refreshToken)
        store.setProfileId(null)
        _state.value = SessionState.LoggedIn(setupPending = setupPending, profileSelected = false)
    }
}
