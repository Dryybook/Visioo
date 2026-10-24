package com.visioo.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.visioo.app.R
import com.visioo.core.designsystem.MessageState

@Composable
fun ComingSoonScreen(@StringRes message: Int = R.string.coming_soon) {
    MessageState(stringResource(message))
}
