package com.visioo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooSpacing
import com.visioo.core.designsystem.VisiooWordmark

/** Centered, scrollable column used by every signed-out and setup screen (phone and TV). */
@Composable
fun AuthScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    showWordmark: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.fillMaxSize().background(VisiooColors.Black).safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VisiooSpacing.Lg, vertical = VisiooSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showWordmark) VisiooWordmark()
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = VisiooColors.TextPrimary, textAlign = TextAlign.Center)
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = VisiooColors.TextSecondary, textAlign = TextAlign.Center)
            }
            content()
        }
    }
}

@Composable
fun ErrorText(message: String?) {
    if (message != null) {
        Text(message, style = MaterialTheme.typography.labelMedium, color = VisiooColors.Error, textAlign = TextAlign.Center)
    }
}
