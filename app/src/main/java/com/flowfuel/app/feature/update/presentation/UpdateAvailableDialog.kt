package com.flowfuel.app.feature.update.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.flowfuel.app.core.designsystem.components.FFDialog
import com.flowfuel.app.core.designsystem.theme.FFTheme

@Composable
fun UpdateAvailableDialog(
    state: UpdateUiState,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Available -> FFDialog(
            title = "Nova versão disponível",
            message = buildString {
                append("FlowFuel ${state.info.versionLabel} já está disponível.")
                state.info.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                    append("\n\n")
                    append(it)
                }
            },
            confirmText = "Atualizar",
            dismissText = "Depois",
            onConfirm = onUpdateClick,
            onDismiss = onDismiss,
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Baixando atualização") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(FFTheme.spacing.md))
                    Text("Baixando FlowFuel ${state.info.versionLabel}...")
                }
            },
        )

        else -> Unit
    }
}
