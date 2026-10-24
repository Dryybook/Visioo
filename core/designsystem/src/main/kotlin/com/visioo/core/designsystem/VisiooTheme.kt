package com.visioo.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VisiooColorScheme = darkColorScheme(
    primary = VisiooColors.Violet,
    onPrimary = VisiooColors.TextPrimary,
    secondary = VisiooColors.VioletBright,
    tertiary = VisiooColors.VioletSoft,
    background = VisiooColors.Black,
    onBackground = VisiooColors.TextPrimary,
    surface = VisiooColors.Surface,
    onSurface = VisiooColors.TextPrimary,
    surfaceVariant = VisiooColors.SurfaceElevated,
    onSurfaceVariant = VisiooColors.TextSecondary,
    outline = VisiooColors.TextMuted,
    error = VisiooColors.Error,
)

private fun visiooTypography(tv: Boolean): Typography {
    val hero = if (tv) 56.sp else 36.sp
    val section = if (tv) 26.sp else 20.sp
    val card = if (tv) 18.sp else 14.sp
    val body = if (tv) 18.sp else 15.sp
    val meta = if (tv) 14.sp else 12.sp
    return Typography(
        displaySmall = TextStyle(fontSize = hero, fontWeight = FontWeight.Bold, lineHeight = hero * 1.1f),
        titleLarge = TextStyle(fontSize = section, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = card, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = body),
        bodyMedium = TextStyle(fontSize = body),
        labelLarge = TextStyle(fontSize = body, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = meta),
        labelSmall = TextStyle(fontSize = meta),
    )
}

@Composable
fun VisiooTheme(isTv: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VisiooColorScheme,
        typography = visiooTypography(isTv),
        shapes = Shapes(
            small = VisiooShapes.Button,
            medium = VisiooShapes.Card,
            large = VisiooShapes.Sheet,
        ),
        content = content,
    )
}
