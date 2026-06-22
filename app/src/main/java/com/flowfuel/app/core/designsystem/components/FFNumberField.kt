package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation

enum class FFNumberKind { Decimal, Currency, Odometer, WholeNumber }

@Composable
fun FFNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    kind: FFNumberKind = FFNumberKind.Decimal,
    errorText: String? = null,
    helper: String? = null,
    leadingIcon: ImageVector? = null,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
) {
    val sanitized = { input: String ->
        val cleaned = when (kind) {
            FFNumberKind.Odometer ->
                input.filter(Char::isDigit).take(OdometerVisualTransformation.MAX_DIGITS)
            FFNumberKind.WholeNumber ->
                input.filter(Char::isDigit).take(WholeNumberVisualTransformation.MAX_DIGITS)
            else ->
                input.filter { it.isDigit() || it == ',' || it == '.' }
        }
        onValueChange(cleaned)
    }
    val keyboardType = when (kind) {
        FFNumberKind.Odometer, FFNumberKind.WholeNumber -> KeyboardType.Number
        else -> KeyboardType.Decimal
    }
    val visualTransformation = when (kind) {
        FFNumberKind.Odometer -> OdometerVisualTransformation()
        FFNumberKind.WholeNumber -> WholeNumberVisualTransformation()
        else -> VisualTransformation.None
    }
    FFTextField(
        value = value,
        onValueChange = sanitized,
        label = label,
        modifier = modifier,
        leadingIcon = leadingIcon,
        helper = helper,
        errorText = errorText,
        keyboardType = keyboardType,
        visualTransformation = visualTransformation,
        imeAction = imeAction,
        keyboardActions = keyboardActions,
        enabled = enabled,
    )
}
