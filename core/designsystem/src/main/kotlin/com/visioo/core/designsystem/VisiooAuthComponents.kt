package com.visioo.core.designsystem

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Text wordmark: violet V and white ISIOO. Placeholder until the final vector logo is exported. */
@Composable
fun VisiooWordmark(modifier: Modifier = Modifier, size: TextUnit = 40.sp) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = VisiooColors.VioletBright)) { append("V") }
            withStyle(SpanStyle(color = VisiooColors.TextPrimary)) { append("ISIOO") }
        },
        fontSize = size,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}

@Composable
fun VisiooTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    showLabel: String? = null,
    hideLabel: String? = null,
    errorText: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        trailingIcon = if (isPassword && showLabel != null && hideLabel != null) {
            {
                TextButton(onClick = { revealed = !revealed }) {
                    Text(if (revealed) hideLabel else showLabel, color = VisiooColors.VioletSoft, style = MaterialTheme.typography.labelMedium)
                }
            }
        } else null,
        shape = VisiooShapes.Button,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VisiooColors.VioletBright,
            unfocusedBorderColor = VisiooColors.SurfaceElevated,
            focusedContainerColor = VisiooColors.Surface,
            unfocusedContainerColor = VisiooColors.Surface,
            focusedTextColor = VisiooColors.TextPrimary,
            unfocusedTextColor = VisiooColors.TextPrimary,
            focusedLabelColor = VisiooColors.VioletSoft,
            unfocusedLabelColor = VisiooColors.TextMuted,
            cursorColor = VisiooColors.VioletBright,
            errorBorderColor = VisiooColors.Error,
            errorSupportingTextColor = VisiooColors.Error,
        ),
    )
}
