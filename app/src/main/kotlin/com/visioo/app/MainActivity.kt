package com.visioo.app

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.visioo.app.ui.PipState
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

    /** Leaving the app while a video plays on a phone shrinks it into a floating window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipState.active && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.inPip.value = isInPictureInPictureMode
    }
}

private fun Context.isTelevision(): Boolean {
    val uiMode = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
