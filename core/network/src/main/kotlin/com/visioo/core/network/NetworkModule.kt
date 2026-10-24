package com.visioo.core.network

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object NetworkModule {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun baseClient() = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)

    /**
     * Builds the API with bearer auth, the `X-Profile-Id` header and automatic token refresh.
     * [onSessionExpired] runs when the server rejects the refresh token.
     */
    fun createApi(baseUrl: String, store: SessionStore, onSessionExpired: () -> Unit = {}): VisiooApi {
        // Refresh goes through a client with no authenticator, so it can never recurse.
        val refreshApi = retrofit(baseUrl, baseClient().build()).create(VisiooApi::class.java)
        val authenticator = TokenAuthenticator(
            store = store,
            refresh = { token ->
                try {
                    RefreshResult.Success(refreshApi.refresh(RefreshRequest(token)))
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 401 || e.code() == 403) RefreshResult.Rejected else RefreshResult.Unavailable
                } catch (e: Exception) {
                    RefreshResult.Unavailable
                }
            },
            onSessionExpired = onSessionExpired,
        )
        val client = baseClient()
            .addInterceptor(headersInterceptor(store))
            .authenticator(authenticator)
            .build()
        return retrofit(baseUrl, client).create(VisiooApi::class.java)
    }

    private fun headersInterceptor(store: SessionStore) = Interceptor { chain ->
        val builder = chain.request().newBuilder()
        store.accessToken()?.let { builder.header("Authorization", "Bearer $it") }
        store.profileId()?.let { builder.header("X-Profile-Id", it) }
        chain.proceed(builder.build())
    }
}
