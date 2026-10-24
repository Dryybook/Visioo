package com.visioo.app.data

import com.visioo.app.BuildConfig
import com.visioo.core.network.NetworkModule
import com.visioo.core.network.VisiooApi

class AppContainer {
    val api: VisiooApi by lazy { NetworkModule.createApi(BuildConfig.API_BASE_URL) }
    val homeRepository: HomeRepository by lazy { RemoteHomeRepository(api) }
}
