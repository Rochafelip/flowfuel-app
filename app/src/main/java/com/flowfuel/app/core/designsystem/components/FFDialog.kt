package com.flowfuel.app.core.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

enum class FFDialogKind { Confirm, Info, Destructive }

@Composable
fun FFDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    kind: FFDialogKind = FFDialogKind.Confirm,
    dismissText: String? = "Cancelar",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmText,
                    color = if (kind == FFDialogKind.Destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = dismissText?.let {
            { TextButton(onClick = onDismiss) { Text(it) } }
        },
    )
}
