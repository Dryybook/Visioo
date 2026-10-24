package com.visioo.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.visioo.app.R
import com.visioo.app.data.AccountRepository
import com.visioo.app.ui.containerViewModel
import com.visioo.app.ui.messageRes
import com.visioo.core.designsystem.LoadingState
import com.visioo.core.designsystem.MessageState
import com.visioo.core.designsystem.VisiooButton
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooSpacing
import com.visioo.core.network.MeDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AccountUiState {
    data object Loading : AccountUiState
    data class Error(val message: Int) : AccountUiState
    data class Ready(val me: MeDto) : AccountUiState
}

class AccountViewModel(private val repository: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = AccountUiState.Loading
        viewModelScope.launch {
            repository.me().fold(
                onSuccess = { _state.value = AccountUiState.Ready(it) },
                onFailure = { _state.value = AccountUiState.Error(it.messageRes()) },
            )
        }
    }
}

@Composable
fun AccountScreen(
    onActivateVip: () -> Unit,
    onRecovery: () -> Unit,
    onSwitchProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    val vm = containerViewModel { AccountViewModel(it.accountRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        AccountUiState.Loading -> LoadingState()
        is AccountUiState.Error -> MessageState(stringResource(s.message), actionLabel = stringResource(R.string.action_retry), onAction = vm::load)
        is AccountUiState.Ready -> Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(VisiooSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md),
        ) {
            Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleLarge, color = VisiooColors.TextPrimary)
            Text(s.me.email, style = MaterialTheme.typography.bodyLarge, color = VisiooColors.TextSecondary)
            Text(
                if (s.me.vip.active) stringResource(R.string.vip_status_active, s.me.vip.endsAt?.take(10).orEmpty()) else stringResource(R.string.vip_status_inactive),
                style = MaterialTheme.typography.bodyLarge,
                color = if (s.me.vip.active) VisiooColors.VioletSoft else VisiooColors.TextMuted,
            )
            VisiooButton(stringResource(if (s.me.vip.active) R.string.vip_extend else R.string.vip_title), onActivateVip, Modifier.fillMaxWidth())
            VisiooButton(
                stringResource(if (s.me.hasRecovery) R.string.recovery_replace else R.string.recovery_create),
                onRecovery, Modifier.fillMaxWidth(), primary = false,
            )
            VisiooButton(stringResource(R.string.account_switch_profile), onSwitchProfile, Modifier.fillMaxWidth(), primary = false)
            VisiooButton(stringResource(R.string.account_logout), onLogout, Modifier.fillMaxWidth(), primary = false)
        }
    }
}
