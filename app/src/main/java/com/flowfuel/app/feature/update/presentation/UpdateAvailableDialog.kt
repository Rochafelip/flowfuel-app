package com.flowfuel.app.feature.update.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onHideDownload: () -> Unit,
    onInstallClick: () -> Unit,
    onDismissReadyToInstall: () -> Unit,
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
            onDismissRequest = onHideDownload,
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onHideDownload) { Text("Ocultar") }
            },
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

        is UpdateUiState.ReadyToInstall -> FFDialog(
            title = "Atualização pronta",
            message = "A nova versão do FlowFuel já foi baixada e está pronta para ser instalada.",
            confirmText = "Instalar",
            dismissText = "Depois",
            onConfirm = onInstallClick,
            onDismiss = onDismissReadyToInstall,
        )

        else -> Unit
    }
}
