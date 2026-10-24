package com.visioo.app.feature.auth

import android.app.Activity
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visioo.app.BuildConfig
import com.visioo.app.R
import com.visioo.app.ui.AuthScaffold
import com.visioo.app.ui.ErrorText
import com.visioo.app.ui.containerViewModel
import com.visioo.core.designsystem.VisiooButton
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooSpacing
import com.visioo.core.designsystem.VisiooTextField
import com.visioo.core.designsystem.VisiooWordmark
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onStart: () -> Unit, onLogin: () -> Unit) {
    AuthScaffold(showWordmark = false) {
        Spacer(Modifier.height(48.dp))
        VisiooWordmark(size = 56.sp)
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.displaySmall, color = VisiooColors.TextPrimary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.onboarding_subtitle), style = MaterialTheme.typography.bodyLarge, color = VisiooColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        VisiooButton(stringResource(R.string.onboarding_start), onStart, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.onboarding_have_account), color = VisiooColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onLogin) { Text(stringResource(R.string.login_submit), color = VisiooColors.VioletSoft) }
        }
    }
}

@Composable
fun LoginScreen(isTv: Boolean, onRegister: () -> Unit, onForgot: () -> Unit) {
    val vm = containerViewModel { AuthViewModel(it.sessionRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var fieldError by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleEnabled = !isTv && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    AuthScaffold(title = stringResource(R.string.login_title), subtitle = stringResource(R.string.login_subtitle)) {
        if (googleEnabled) {
            VisiooButton(stringResource(R.string.login_google), {
                scope.launch {
                    requestGoogleIdToken(context, BuildConfig.GOOGLE_WEB_CLIENT_ID)
                        .onSuccess(vm::google)
                        .onFailure { vm.showError(R.string.error_google) }
                }
            }, Modifier.fillMaxWidth(), primary = false)
            Text(stringResource(R.string.login_or), color = VisiooColors.TextMuted, style = MaterialTheme.typography.labelMedium)
        }
        VisiooTextField(email, { email = it; vm.clearError() }, stringResource(R.string.field_email), Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email,
            errorText = if (fieldError && !AuthValidation.isValidEmail(email)) stringResource(R.string.validation_email) else null)
        VisiooTextField(password, { password = it; vm.clearError() }, stringResource(R.string.field_password), Modifier.fillMaxWidth(), isPassword = true,
            showLabel = stringResource(R.string.password_show), hideLabel = stringResource(R.string.password_hide),
            errorText = if (fieldError && password.isEmpty()) stringResource(R.string.validation_password) else null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onForgot) { Text(stringResource(R.string.login_forgot), color = VisiooColors.VioletSoft, style = MaterialTheme.typography.labelMedium) }
        }
        ErrorText(state.error?.let { stringResource(it) })
        VisiooButton(
            if (state.loading) "…" else stringResource(R.string.login_submit),
            {
                fieldError = true
                if (AuthValidation.isValidEmail(email) && password.isNotEmpty()) vm.login(email, password)
            },
            Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.login_no_account), color = VisiooColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRegister) { Text(stringResource(R.string.login_register), color = VisiooColors.VioletSoft) }
        }
    }
}

@Composable
fun RegisterScreen(onLogin: () -> Unit) {
    val vm = containerViewModel { AuthViewModel(it.sessionRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var fieldError by rememberSaveable { mutableStateOf(false) }

    AuthScaffold(title = stringResource(R.string.register_title), subtitle = stringResource(R.string.register_subtitle)) {
        VisiooTextField(name, { name = it }, stringResource(R.string.field_name), Modifier.fillMaxWidth(),
            errorText = if (fieldError && name.isBlank()) stringResource(R.string.validation_name) else null)
        VisiooTextField(email, { email = it; vm.clearError() }, stringResource(R.string.field_email), Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email,
            errorText = if (fieldError && !AuthValidation.isValidEmail(email)) stringResource(R.string.validation_email) else null)
        VisiooTextField(password, { password = it }, stringResource(R.string.field_password), Modifier.fillMaxWidth(), isPassword = true,
            showLabel = stringResource(R.string.password_show), hideLabel = stringResource(R.string.password_hide),
            errorText = if (fieldError && !AuthValidation.isValidPassword(password)) stringResource(R.string.validation_password) else null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(accepted, { accepted = it }, colors = CheckboxDefaults.colors(checkedColor = VisiooColors.Violet, uncheckedColor = VisiooColors.TextMuted))
            Text(stringResource(R.string.register_terms), color = VisiooColors.TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
        }
        if (fieldError && !accepted) ErrorText(stringResource(R.string.validation_terms))
        ErrorText(state.error?.let { stringResource(it) })
        VisiooButton(
            if (state.loading) "…" else stringResource(R.string.register_submit),
            {
                fieldError = true
                if (name.isNotBlank() && AuthValidation.isValidEmail(email) && AuthValidation.isValidPassword(password) && accepted) vm.register(email, password, name)
            },
            Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.onboarding_have_account), color = VisiooColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onLogin) { Text(stringResource(R.string.login_submit), color = VisiooColors.VioletSoft) }
        }
    }
}

@Composable
fun RecoverScreen(onBack: () -> Unit) {
    val vm = containerViewModel { AuthViewModel(it.sessionRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var phrase by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var fieldError by rememberSaveable { mutableStateOf(false) }

    AuthScaffold(title = stringResource(R.string.recover_title), subtitle = stringResource(R.string.recover_subtitle)) {
        VisiooTextField(email, { email = it; vm.clearError() }, stringResource(R.string.field_email), Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email,
            errorText = if (fieldError && !AuthValidation.isValidEmail(email)) stringResource(R.string.validation_email) else null)
        VisiooTextField(phrase, { phrase = it; vm.clearError() }, stringResource(R.string.recover_phrase), Modifier.fillMaxWidth(), singleLine = false)
        VisiooTextField(password, { password = it }, stringResource(R.string.field_new_password), Modifier.fillMaxWidth(), isPassword = true,
            showLabel = stringResource(R.string.password_show), hideLabel = stringResource(R.string.password_hide),
            errorText = if (fieldError && !AuthValidation.isValidPassword(password)) stringResource(R.string.validation_password) else null)
        ErrorText(state.error?.let { stringResource(it) })
        VisiooButton(
            if (state.loading) "…" else stringResource(R.string.recover_submit),
            {
                fieldError = true
                if (AuthValidation.isValidEmail(email) && AuthValidation.isValidPassword(password) && phrase.isNotBlank()) vm.recover(email, phrase, password)
            },
            Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back), color = VisiooColors.VioletSoft) }
    }
}

/** Blocks screenshots and screen recording while sensitive content is on screen. */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
