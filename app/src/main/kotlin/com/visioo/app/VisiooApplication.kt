package com.visioo.app

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import com.visioo.app.data.AppContainer

class VisiooApplication : Application() {
    val container: AppContainer by lazy {
        val uiMode = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        AppContainer(this, isTv = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION).also {
            // Wire the expiry callback before any request can run.
            it.sessionRepository
        }
    }
}
