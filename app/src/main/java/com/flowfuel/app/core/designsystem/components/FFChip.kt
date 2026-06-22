package com.flowfuel.app.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class FFChipKind { Filter, Assist, Input }

@Composable
fun FFChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: FFChipKind = FFChipKind.Filter,
    selected: Boolean = false,
    leadingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    val leading: (@Composable () -> Unit)? = leadingIcon?.let {
        { Icon(it, contentDescription = null) }
    }
    when (kind) {
        FFChipKind.Filter -> FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
            leadingIcon = leading,
            modifier = modifier,
        )
        FFChipKind.Assist -> AssistChip(
            onClick = onClick,
            label = { Text(label) },
            leadingIcon = leading,
            modifier = modifier,
        )
        FFChipKind.Input -> InputChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
            leadingIcon = leading,
            trailingIcon = onTrailingClick?.let { handler ->
                {
                    IconButton(onClick = handler) {
                        Icon(Icons.Default.Close, contentDescription = "Remover")
                    }
                }
            },
            modifier = modifier,
        )
    }
}
