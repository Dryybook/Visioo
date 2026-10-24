package com.visioo.app.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.visioo.app.R
import com.visioo.app.data.HomeSection
import com.visioo.app.data.SectionLayout
import com.visioo.core.designsystem.LoadingState
import com.visioo.core.designsystem.MessageState
import com.visioo.core.designsystem.PosterCard
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooSpacing
import com.visioo.core.network.ApiError

@Composable
fun HomeScreen(
    isTv: Boolean,
    onContentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        HomeUiState.Loading -> LoadingState(modifier)
        HomeUiState.Empty -> MessageState(stringResource(R.string.home_empty), modifier)
        is HomeUiState.Error -> MessageState(
            message = stringResource(s.error.messageRes()),
            modifier = modifier,
            actionLabel = stringResource(R.string.action_retry),
            onAction = viewModel::load,
        )
        is HomeUiState.Success -> HomeSections(s.sections, isTv, onContentClick, modifier)
    }
}

@Composable
private fun HomeSections(
    sections: List<HomeSection>,
    isTv: Boolean,
    onContentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val edge = if (isTv) VisiooSpacing.Xxl else VisiooSpacing.Md
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = VisiooSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Lg),
    ) {
        items(sections, key = { it.id }) { section ->
            Column(verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md)) {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = VisiooColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = edge),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = edge, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Md),
                ) {
                    val landscape = section.layout == SectionLayout.Landscape
                    items(section.items, key = { it.id }) { item ->
                        PosterCard(
                            title = item.title,
                            imageUrl = item.imageUrl,
                            isVip = item.isVip,
                            landscape = landscape,
                            cardWidth = when {
                                landscape && isTv -> 300.dp
                                landscape -> 220.dp
                                isTv -> 180.dp
                                else -> 130.dp
                            },
                            onClick = { onContentClick(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@StringRes
private fun ApiError.messageRes(): Int = when (this) {
    ApiError.Network -> R.string.error_network
    ApiError.Unauthorized -> R.string.error_unauthorized
    ApiError.Forbidden -> R.string.error_forbidden
    ApiError.NotFound -> R.string.error_not_found
    ApiError.RateLimited -> R.string.error_rate_limited
    ApiError.Server -> R.string.error_server
    ApiError.Unknown -> R.string.error_unknown
}
