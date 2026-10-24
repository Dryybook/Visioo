package com.visioo.app.feature.vip

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.visioo.app.R
import com.visioo.app.data.AccountRepository
import com.visioo.app.feature.auth.VipKeyFormat
import com.visioo.app.ui.AuthScaffold
import com.visioo.app.ui.ErrorText
import com.visioo.app.ui.containerViewModel
import com.visioo.app.ui.messageRes
import com.visioo.core.designsystem.VisiooButton
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooTextField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VipUiState(val loading: Boolean = false, @StringRes val error: Int? = null, val activated: Boolean = false)

class VipViewModel(private val repository: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(VipUiState())
    val state: StateFlow<VipUiState> = _state.asStateFlow()

    fun activate(formattedKey: String) {
        if (_state.value.loading) return
        _state.value = VipUiState(loading = true)
        viewModelScope.launch {
            // The server is the only authority: nothing is stored locally as a VIP flag.
            val result = repository.activateVip(formattedKey)
            _state.value = VipUiState(error = result.exceptionOrNull()?.messageRes(), activated = result.isSuccess)
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}

@Composable
fun VipActivationScreen(onDone: () -> Unit, onSkip: (() -> Unit)? = null) {
    val vm = containerViewModel { VipViewModel(it.accountRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var key by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.activated) { if (state.activated) onDone() }

    AuthScaffold(title = stringResource(R.string.vip_title), subtitle = stringResource(R.string.vip_subtitle)) {
        VisiooTextField(
            key,
            { key = VipKeyFormat.format(it); vm.clearError() },
            stringResource(R.string.vip_key_hint),
            Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Ascii,
        )
        ErrorText(state.error?.let { stringResource(it) })
        VisiooButton(
            if (state.loading) "…" else stringResource(R.string.vip_activate),
            { if (VipKeyFormat.isComplete(key)) vm.activate(key) },
            Modifier.fillMaxWidth(),
        )
        if (onSkip != null) TextButton(onClick = onSkip) { Text(stringResource(R.string.action_skip), color = VisiooColors.VioletSoft) }
    }
}
