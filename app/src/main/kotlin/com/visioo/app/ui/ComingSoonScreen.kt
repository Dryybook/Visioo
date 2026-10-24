package com.visioo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.visioo.app.R
import com.visioo.core.designsystem.MessageState

@Composable
fun ComingSoonScreen() {
    MessageState(stringResource(R.string.coming_soon))
}
