package com.visioo.app.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** Bridge between the activity (which owns picture-in-picture) and the player screen. */
object PipState {
    /** True only while a phone/tablet player is on screen: that is when leaving the app should enter PiP. */
    @Volatile var active: Boolean = false
    val inPip = MutableStateFlow(false)
}
