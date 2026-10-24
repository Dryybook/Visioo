package com.visioo.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

sealed interface RefreshResult {
    data class Success(val session: SessionDto) : RefreshResult
    /** The server refused the refresh token: the session is over. */
    data object Rejected : RefreshResult
    /** Offline or server trouble: keep the session and let the call fail. */
    data object Unavailable : RefreshResult
}

/**
 * On a 401, refreshes the tokens once (serialized across threads) and retries the request.
 * Never loops: a request that already failed after a refresh is not retried again.
 */
class TokenAuthenticator(
    private val store: SessionStore,
    private val refresh: suspend (refreshToken: String) -> RefreshResult,
    private val onSessionExpired: () -> Unit,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (PUBLIC_SUFFIXES.any { path.endsWith(it) }) return null
        if (priorAttempts(response) >= 1) return null

        synchronized(this) {
            val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
            val current = store.accessToken()
            // Another request already refreshed while this one was waiting.
            if (current != null && current != sent) return withToken(response.request, current)

            val refreshToken = store.refreshToken() ?: return null
            return when (val result = runBlocking { refresh(refreshToken) }) {
                is RefreshResult.Success -> {
                    store.saveTokens(result.session.accessToken, result.session.refreshToken)
                    withToken(response.request, result.session.accessToken)
                }
                RefreshResult.Rejected -> {
                    store.clear()
                    onSessionExpired()
                    null
                }
                RefreshResult.Unavailable -> null
            }
        }
    }

    private fun withToken(request: Request, token: String) =
        request.newBuilder().header("Authorization", "Bearer $token").build()

    private fun priorAttempts(response: Response): Int {
        var count = 0
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        val PUBLIC_SUFFIXES = listOf("/auth/login", "/auth/register", "/auth/google", "/auth/recover", "/auth/refresh")
    }
}
