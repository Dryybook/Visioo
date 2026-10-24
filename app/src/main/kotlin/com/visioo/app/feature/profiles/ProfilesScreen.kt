package com.visioo.app.feature.profiles

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.visioo.app.R
import com.visioo.app.data.AccountRepository
import com.visioo.app.ui.ErrorText
import com.visioo.app.ui.containerViewModel
import com.visioo.app.ui.messageRes
import com.visioo.core.designsystem.LoadingState
import com.visioo.core.designsystem.MessageState
import com.visioo.core.designsystem.VisiooButton
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooSpacing
import com.visioo.core.designsystem.VisiooTextField
import com.visioo.core.designsystem.visiooFocus
import com.visioo.core.network.ProfileDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_PROFILES = 5

data class ProfilesUiState(
    val loading: Boolean = true,
    val profiles: List<ProfileDto> = emptyList(),
    @StringRes val error: Int? = null,
    val managing: Boolean = false,
    /** null: no dialog. A blank-id profile means "creating". */
    val editing: ProfileDto? = null,
)

class ProfilesViewModel(private val repository: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.profiles().fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, profiles = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.messageRes()) } },
            )
        }
    }

    fun toggleManaging() = _state.update { it.copy(managing = !it.managing) }
    fun startCreate() = _state.update { it.copy(editing = ProfileDto(id = "", name = ""), error = null) }
    fun startEdit(p: ProfileDto) = _state.update { it.copy(editing = p, error = null) }
    fun dismissDialog() = _state.update { it.copy(editing = null) }

    fun save(name: String, isChild: Boolean) {
        val target = _state.value.editing ?: return
        viewModelScope.launch {
            val result = if (target.id.isEmpty()) repository.createProfile(name, isChild).map { } else repository.updateProfile(target.id, name, isChild).map { }
            result.fold(
                onSuccess = { _state.update { it.copy(editing = null) }; load() },
                onFailure = { e -> _state.update { it.copy(error = e.messageRes(), editing = null) } },
            )
        }
    }

    fun delete(onDeleted: (String) -> Unit) {
        val target = _state.value.editing ?: return
        viewModelScope.launch {
            repository.deleteProfile(target.id).fold(
                onSuccess = { onDeleted(target.id); _state.update { it.copy(editing = null) }; load() },
                onFailure = { e -> _state.update { it.copy(error = e.messageRes(), editing = null) } },
            )
        }
    }
}

@Composable
fun ProfileSelectScreen(onSelected: (String) -> Unit, onProfileDeleted: (String) -> Unit) {
    val vm = containerViewModel { ProfilesViewModel(it.accountRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxWidth().background(VisiooColors.Black)) {
        when {
            state.loading -> LoadingState()
            state.profiles.isEmpty() && state.error != null ->
                MessageState(stringResource(state.error!!), actionLabel = stringResource(R.string.action_retry), onAction = vm::load)
            else -> Column(
                Modifier.fillMaxWidth().padding(VisiooSpacing.Lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Lg),
            ) {
                Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.titleLarge, color = VisiooColors.TextPrimary)
                ErrorText(state.error?.let { stringResource(it) })
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(VisiooSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Lg),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(state.profiles, key = { it.id }) { profile ->
                        ProfileTile(profile.name, profile.isChild, if (state.managing) "✎" else null) {
                            if (state.managing) vm.startEdit(profile) else onSelected(profile.id)
                        }
                    }
                    if (state.profiles.size < MAX_PROFILES) {
                        item { ProfileTile(stringResource(R.string.profiles_add), false, "+", onClick = vm::startCreate) }
                    }
                }
                VisiooButton(
                    stringResource(if (state.managing) R.string.profiles_done else R.string.profiles_manage),
                    vm::toggleManaging,
                    primary = false,
                )
            }
        }
    }

    state.editing?.let { target ->
        ProfileDialog(
            target = target,
            canDelete = target.id.isNotEmpty() && state.profiles.size > 1,
            onSave = vm::save,
            onDelete = { vm.delete(onProfileDeleted) },
            onDismiss = vm::dismissDialog,
        )
    }
}

@Composable
private fun ProfileTile(name: String, isChild: Boolean, badge: String?, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Sm)) {
        Box(
            Modifier
                .size(96.dp)
                .visiooFocus(CircleShape)
                .clip(CircleShape)
                .background(avatarColor(name))
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(badge ?: name.take(1).uppercase(), fontSize = 36.sp, color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Text(name, style = MaterialTheme.typography.bodyMedium, color = VisiooColors.TextPrimary, maxLines = 1)
        if (isChild) Text(stringResource(R.string.profile_child), style = MaterialTheme.typography.labelSmall, color = VisiooColors.VioletSoft)
    }
}

private val AVATAR_COLORS = listOf(Color(0xFF7C3AED), Color(0xFF5B21B6), Color(0xFF9B5CFF), Color(0xFF4C1D95), Color(0xFF6D28D9))
private fun avatarColor(name: String) = AVATAR_COLORS[(name.hashCode() and Int.MAX_VALUE) % AVATAR_COLORS.size]

@Composable
private fun ProfileDialog(target: ProfileDto, canDelete: Boolean, onSave: (String, Boolean) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember(target.id) { mutableStateOf(target.name) }
    var isChild by remember(target.id) { mutableStateOf(target.isChild) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VisiooColors.Surface,
        title = { Text(stringResource(if (target.id.isEmpty()) R.string.profile_new else R.string.profile_edit), color = VisiooColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md)) {
                VisiooTextField(name, { name = it.take(30) }, stringResource(R.string.profile_name), Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_is_child), color = VisiooColors.TextPrimary)
                    Switch(isChild, { isChild = it }, colors = SwitchDefaults.colors(checkedTrackColor = VisiooColors.Violet))
                }
                if (canDelete) TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = VisiooColors.Error) }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name, isChild) }) { Text(stringResource(R.string.action_save), color = VisiooColors.VioletSoft) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = VisiooColors.TextSecondary) } },
    )
}
