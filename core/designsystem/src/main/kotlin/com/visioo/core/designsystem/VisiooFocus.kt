package com.visioo.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * TV/pointer focus treatment: scale 1.00 -> 1.04 and a violet ring.
 * Must be placed before `clickable`/`focusable` in the modifier chain.
 */
fun Modifier.visiooFocus(shape: Shape = VisiooShapes.Card): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) VisiooMotion.FocusScale else 1f,
        animationSpec = VisiooMotion.focus(),
        label = "visiooFocusScale",
    )
    this
        .onFocusChanged { focused = it.isFocused }
        .scale(scale)
        .border(if (focused) 3.dp else 0.dp, VisiooColors.VioletBright, shape)
}
