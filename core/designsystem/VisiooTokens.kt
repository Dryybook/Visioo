package com.visioo.core.designsystem

import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object VisiooColors {
    val Black = Color(0xFF050507)
    val DeepBlack = Color(0xFF09090D)
    val Surface = Color(0xFF111117)
    val SurfaceElevated = Color(0xFF171720)
    val Violet = Color(0xFF7C3AED)
    val VioletBright = Color(0xFF9B5CFF)
    val VioletSoft = Color(0xFFB88CFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA7A7B2)
    val TextMuted = Color(0xFF6F6F7A)
    val Success = Color(0xFF39D98A)
    val Warning = Color(0xFFF5B942)
    val Error = Color(0xFFFF5C68)
}

object VisiooSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 32.dp
    val Xxl = 48.dp
}

object VisiooShapes {
    val Card = RoundedCornerShape(12.dp)
    val Button = RoundedCornerShape(10.dp)
    val Chip = RoundedCornerShape(percent = 50)
    val Sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}

object VisiooMotion {
    const val MicroMs = 140
    const val StandardMs = 220
    const val ModalMs = 280
    const val HeroMs = 450
    const val FocusMs = 160
    const val FocusScale = 1.04f

    fun <T> focus() = tween<T>(FocusMs)
    fun <T> standard() = tween<T>(StandardMs)
}
