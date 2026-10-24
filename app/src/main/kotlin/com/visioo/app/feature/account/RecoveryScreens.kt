package com.visioo.app.feature.account

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.visioo.app.R
import com.visioo.app.data.AccountRepository
import com.visioo.app.feature.auth.RecoveryChallenge
import com.visioo.app.feature.auth.SecureWindow
import com.visioo.app.ui.AuthScaffold
import com.visioo.app.ui.ErrorText
import com.visioo.app.ui.containerViewModel
import com.visioo.app.ui.messageRes
import com.visioo.core.designsystem.LoadingState
import com.visioo.core.designsystem.VisiooButton
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooSpacing
import com.visioo.core.designsystem.VisiooTextField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RecoveryUiState {
    data object Loading : RecoveryUiState
    data class Error(@StringRes val message: Int) : RecoveryUiState
    data class Show(val words: List<String>) : RecoveryUiState
    data class Confirm(val challenge: RecoveryChallenge, val wrong: Boolean = false) : RecoveryUiState
}

class RecoveryViewModel(private val repository: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow<RecoveryUiState>(RecoveryUiState.Loading)
    val state: StateFlow<RecoveryUiState> = _state.asStateFlow()

    // Held only in memory for the confirmation step. Never persisted or logged.
    private var words: List<String> = emptyList()

    init {
        load()
    }

    fun load() {
        _state.value = RecoveryUiState.Loading
        viewModelScope.launch {
            repository.createRecoveryPhrase().fold(
                onSuccess = { words = it; _state.value = RecoveryUiState.Show(it) },
                onFailure = { _state.value = RecoveryUiState.Error(it.messageRes()) },
            )
        }
    }

    fun startConfirm() {
        _state.value = RecoveryUiState.Confirm(RecoveryChallenge.create(words))
    }

    fun backToPhrase() {
        _state.value = RecoveryUiState.Show(words)
    }

    /** Returns true when the answers are right. */
    fun check(answers: List<String>): Boolean {
        val current = _state.value as? RecoveryUiState.Confirm ?: return false
        val ok = current.challenge.check(answers)
        if (!ok) _state.value = current.copy(wrong = true)
        else words = emptyList()
        return ok
    }
}

@Composable
fun RecoveryFlowScreen(onFinished: () -> Unit, onSkip: (() -> Unit)? = null) {
    val vm = containerViewModel { RecoveryViewModel(it.accountRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    SecureWindow()

    when (val s = state) {
        RecoveryUiState.Loading -> LoadingState()
        is RecoveryUiState.Error -> AuthScaffold(title = stringResource(R.string.recovery_title)) {
            ErrorText(stringResource(s.message))
            VisiooButton(stringResource(R.string.action_retry), vm::load, Modifier.fillMaxWidth())
            if (onSkip != null) TextButton(onClick = onSkip) { Text(stringResource(R.string.action_skip), color = VisiooColors.VioletSoft) }
        }
        is RecoveryUiState.Show -> AuthScaffold(title = stringResource(R.string.recovery_title), subtitle = stringResource(R.string.recovery_subtitle)) {
            WordGrid(s.words)
            Text(
                stringResource(R.string.recovery_warning),
                style = MaterialTheme.typography.labelMedium,
                color = VisiooColors.Warning,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VisiooColors.Surface).padding(VisiooSpacing.Md),
            )
            VisiooButton(stringResource(R.string.recovery_noted), vm::startConfirm, Modifier.fillMaxWidth())
            if (onSkip != null) TextButton(onClick = onSkip) { Text(stringResource(R.string.action_skip), color = VisiooColors.VioletSoft) }
        }
        is RecoveryUiState.Confirm -> {
            val answers = remember(s.challenge) { mutableStateListOf(*Array(s.challenge.positions.size) { "" }) }
            AuthScaffold(title = stringResource(R.string.recovery_confirm_title), subtitle = stringResource(R.string.recovery_confirm_subtitle)) {
                s.challenge.positions.forEachIndexed { i, position ->
                    VisiooTextField(answers[i], { answers[i] = it }, stringResource(R.string.recovery_word_n, position + 1), Modifier.fillMaxWidth())
                }
                if (s.wrong) ErrorText(stringResource(R.string.recovery_confirm_wrong))
                VisiooButton(stringResource(R.string.recovery_confirm_submit), { if (vm.check(answers.toList())) onFinished() }, Modifier.fillMaxWidth())
                TextButton(onClick = vm::backToPhrase) { Text(stringResource(R.string.recovery_show_again), color = VisiooColors.VioletSoft) }
            }
        }
    }
}

@Composable
private fun WordGrid(words: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm)) {
        words.chunked(3).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm)) {
                chunk.forEachIndexed { col, word ->
                    Text(
                        "${row * 3 + col + 1}  $word",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisiooColors.TextPrimary,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VisiooColors.Surface)
                            .padding(horizontal = VisiooSpacing.Sm + 2.dp, vertical = VisiooSpacing.Md - 4.dp),
                    )
                }
            }
        }
    }
}
