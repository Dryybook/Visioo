package com.visioo.app

import android.app.Application
import com.visioo.app.data.AppContainer

class VisiooApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
