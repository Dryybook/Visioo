package com.visioo.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun VisiooButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
) {
    Box(
        modifier = modifier
            .visiooFocus(VisiooShapes.Button)
            .clip(VisiooShapes.Button)
            .background(if (primary) VisiooColors.Violet else VisiooColors.SurfaceElevated)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = VisiooSpacing.Lg, vertical = VisiooSpacing.Md - 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = VisiooColors.TextPrimary)
    }
}

@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isVip: Boolean = false,
    landscape: Boolean = false,
    cardWidth: androidx.compose.ui.unit.Dp = if (landscape) 220.dp else 130.dp,
) {
    Column(modifier.width(cardWidth), verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                .visiooFocus(VisiooShapes.Card)
                .clip(VisiooShapes.Card)
                .background(VisiooColors.SurfaceElevated)
                .clickable(role = Role.Button, onClick = onClick),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isVip) {
                Text(
                    "VIP",
                    style = MaterialTheme.typography.labelSmall,
                    color = VisiooColors.TextPrimary,
                    modifier = Modifier
                        .padding(VisiooSpacing.Sm)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(VisiooColors.Violet)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = VisiooColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = VisiooColors.Violet)
    }
}

@Composable
fun MessageState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(VisiooSpacing.Lg), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = VisiooColors.TextSecondary,
            )
            if (actionLabel != null && onAction != null) {
                VisiooButton(actionLabel, onAction)
            }
        }
    }
}

/** Remote image with the app's cropping default. Keeps Coil an implementation detail of the design system. */
@Composable
fun VisiooImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(model = url, contentDescription = contentDescription, contentScale = contentScale, modifier = modifier)
}
