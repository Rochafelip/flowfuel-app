package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

enum class FFTextFieldVariant { Filled, Outlined }

@Composable
fun FFTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    variant: FFTextFieldVariant = FFTextFieldVariant.Outlined,
    placeholder: String? = null,
    helper: String? = null,
    errorText: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isPassword: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val isError = errorText != null

    val effectiveVisualTransformation: VisualTransformation = when {
        isPassword && !passwordVisible -> PasswordVisualTransformation()
        else -> visualTransformation
    }

    val effectiveTrailing: (@Composable () -> Unit)? = when {
        isPassword -> {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            }
        }
        trailingIcon != null -> {
            {
                if (onTrailingClick != null) {
                    IconButton(onClick = onTrailingClick) {
                        Icon(trailingIcon, contentDescription = null)
                    }
                } else {
                    Icon(trailingIcon, contentDescription = null)
                }
            }
        }
        else -> null
    }

    val leading: (@Composable () -> Unit)? = leadingIcon?.let {
        { Icon(it, contentDescription = null) }
    }

    val supporting: (@Composable () -> Unit)? = when {
        errorText != null -> { { Text(errorText) } }
        helper != null -> { { Text(helper) } }
        else -> null
    }

    val keyboardOptions = KeyboardOptions(
        keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
        imeAction = imeAction,
    )

    when (variant) {
        FFTextFieldVariant.Outlined -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            leadingIcon = leading,
            trailingIcon = effectiveTrailing,
            supportingText = supporting,
            isError = isError,
            singleLine = singleLine,
            enabled = enabled,
            visualTransformation = effectiveVisualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = modifier.fillMaxWidth(),
        )
        FFTextFieldVariant.Filled -> TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            leadingIcon = leading,
            trailingIcon = effectiveTrailing,
            supportingText = supporting,
            isError = isError,
            singleLine = singleLine,
            enabled = enabled,
            visualTransformation = effectiveVisualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = modifier.fillMaxWidth(),
        )
    }
}
