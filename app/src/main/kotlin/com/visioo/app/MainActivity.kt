package com.visioo.app

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.visioo.app.ui.VisiooApp
import com.visioo.core.designsystem.VisiooTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val isTv = remember { context.isTelevision() }
            VisiooTheme(isTv = isTv) {
                VisiooApp(isTv)
            }
        }
    }
}

private fun Context.isTelevision(): Boolean {
    val uiMode = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
