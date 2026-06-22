package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.theme.FFTheme

enum class FFButtonVariant { Primary, Secondary, Tonal, Text, Destructive }

@Composable
fun FFButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FFButtonVariant = FFButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }

    val mod = modifier
        .heightIn(min = FFTheme.spacing.minTouchTarget)
        .semantics { role = Role.Button }

    when (variant) {
        FFButtonVariant.Primary -> Button(
            onClick = onClick, enabled = enabled && !loading, modifier = mod
        ) { content() }

        FFButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick, enabled = enabled && !loading, modifier = mod
        ) { content() }

        FFButtonVariant.Tonal -> FilledTonalButton(
            onClick = onClick, enabled = enabled && !loading, modifier = mod
        ) { content() }

        FFButtonVariant.Text -> TextButton(
            onClick = onClick, enabled = enabled && !loading, modifier = mod
        ) { content() }

        FFButtonVariant.Destructive -> Button(
            onClick = onClick, enabled = enabled && !loading, modifier = mod,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        ) { content() }
    }
}
