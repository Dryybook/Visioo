package com.visioo.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visioo.core.designsystem.VisiooImage
import com.visioo.app.R
import com.visioo.app.data.ContentSummary
import com.visioo.app.data.HomeContent
import com.visioo.app.data.SectionLayout
import com.visioo.app.ui.containerViewModel
import com.visioo.app.ui.messageRes
import com.visioo.core.designsystem.LoadingState
import com.visioo.core.designsystem.MessageState
import com.visioo.core.designsystem.PosterCard
import com.visioo.core.designsystem.VisiooButton
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooSpacing
import com.visioo.core.network.ApiError
import com.visioo.core.network.ApiException

@Composable
fun HomeScreen(
    isTv: Boolean,
    onContentClick: (ContentSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = containerViewModel { HomeViewModel(it.catalogRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        HomeUiState.Loading -> LoadingState(modifier)
        HomeUiState.Empty -> MessageState(stringResource(R.string.home_empty), modifier)
        is HomeUiState.Error -> MessageState(
            message = stringResource(ApiException(s.error, s.code).messageRes()),
            modifier = modifier,
            actionLabel = stringResource(R.string.action_retry),
            onAction = viewModel::load,
        )
        is HomeUiState.Success -> HomeContentView(s.content, isTv, onContentClick, modifier)
    }
}

@Composable
private fun HomeContentView(content: HomeContent, isTv: Boolean, onContentClick: (ContentSummary) -> Unit, modifier: Modifier) {
    val edge = if (isTv) VisiooSpacing.Xxl else VisiooSpacing.Md
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = VisiooSpacing.Lg), verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Lg)) {
        content.hero.firstOrNull()?.let { hero ->
            item(key = "hero") { HeroBanner(hero, isTv, onClick = { onContentClick(hero) }) }
        }
        items(content.sections, key = { it.id }) { section ->
            Column(verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md)) {
                Text(section.title, style = MaterialTheme.typography.titleLarge, color = VisiooColors.TextPrimary, modifier = Modifier.padding(horizontal = edge))
                LazyRow(contentPadding = PaddingValues(horizontal = edge, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Md)) {
                    val landscape = section.layout == SectionLayout.Landscape
                    items(section.items, key = { it.id }) { item ->
                        PosterCard(
                            title = item.title,
                            imageUrl = if (landscape) item.backdropUrl ?: item.posterUrl else item.posterUrl ?: item.backdropUrl,
                            isVip = item.isVip,
                            landscape = landscape,
                            cardWidth = when {
                                landscape && isTv -> 300.dp
                                landscape -> 220.dp
                                isTv -> 180.dp
                                else -> 130.dp
                            },
                            onClick = { onContentClick(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(item: ContentSummary, isTv: Boolean, onClick: () -> Unit) {
    val edge = if (isTv) VisiooSpacing.Xxl else VisiooSpacing.Md
    Box(Modifier.fillMaxWidth().height(if (isTv) 440.dp else 340.dp).background(VisiooColors.Surface)) {
        (item.backdropUrl ?: item.posterUrl)?.let {
            VisiooImage(it, contentDescription = null, modifier = Modifier.matchParentSize())
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(VisiooColors.Black.copy(alpha = 0.1f), VisiooColors.Black))))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = edge, vertical = VisiooSpacing.Lg), verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm)) {
            Text(item.title, style = MaterialTheme.typography.displaySmall, color = VisiooColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                if (item.isVip) stringResource(R.string.details_vip_badge) else null,
                stringResource(if (item.type == com.visioo.app.data.ContentType.Movie) R.string.type_movie else R.string.type_series),
                item.year?.toString(),
            ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelMedium, color = VisiooColors.TextSecondary)
            item.overview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = VisiooColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 560.dp))
            }
            Row(Modifier.padding(top = VisiooSpacing.Sm)) { VisiooButton(stringResource(R.string.action_watch), onClick) }
        }
    }
}
