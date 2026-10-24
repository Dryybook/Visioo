package com.visioo.app.data

import android.content.Context
import android.os.Build
import com.visioo.app.BuildConfig
import com.visioo.app.data.session.KeystoreSessionStore
import com.visioo.app.data.session.SessionRepository
import com.visioo.core.network.DeviceInfoDto
import com.visioo.core.network.NetworkModule
import com.visioo.core.network.VisiooApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context, isTv: Boolean) {
    private val store = KeystoreSessionStore(context.applicationContext)

    // Set after construction: the api needs the expiry callback, which needs the repository, which needs the api.
    private lateinit var sessionRepo: SessionRepository

    val api: VisiooApi by lazy {
        NetworkModule.createApi(BuildConfig.API_BASE_URL, store) { sessionRepo.onSessionExpired() }
    }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(api, store) {
            DeviceInfoDto(
                installationId = store.installationId(),
                platform = if (isTv) "android_tv" else "android",
                model = Build.MODEL,
                appVersion = BuildConfig.VERSION_NAME,
            )
        }.also { sessionRepo = it }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val playbackRepository: PlaybackRepository by lazy { PlaybackRepository(api, appScope) }
    val catalogRepository: CatalogRepository by lazy { RemoteCatalogRepository(api) }
    val accountRepository: AccountRepository by lazy { AccountRepository(api) }
}
