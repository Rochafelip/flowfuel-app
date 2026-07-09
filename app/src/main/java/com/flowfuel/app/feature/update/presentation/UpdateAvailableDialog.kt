package com.flowfuel.app.feature.update.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.update.domain.model.DownloadProgress

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
        is UpdateUiState.Available -> UpdateDialogShell(
            title = "Nova versão disponível",
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onUpdateClick) { Text("Atualizar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Depois") } },
        ) {
            Text("A versão ${state.info.versionLabel} do FlowFuel já está disponível, com melhorias e correções.")
        }

        is UpdateUiState.Downloading -> UpdateDialogShell(
            title = "Baixando atualização",
            onDismissRequest = onHideDownload,
            confirmButton = {},
            dismissButton = { TextButton(onClick = onHideDownload) { Text("Ocultar") } },
        ) {
            DownloadProgressContent(versionLabel = state.info.versionLabel, progress = state.progress)
        }

        is UpdateUiState.ReadyToInstall -> UpdateDialogShell(
            title = "Atualização pronta",
            onDismissRequest = onDismissReadyToInstall,
            confirmButton = { TextButton(onClick = onInstallClick) { Text("Instalar") } },
            dismissButton = { TextButton(onClick = onDismissReadyToInstall) { Text("Depois") } },
        ) {
            Text("A nova versão do FlowFuel já foi baixada e está pronta para ser instalada.")
        }

        else -> Unit
    }
}

/** Shell comum aos 3 estados: ícone da marca + título + corpo + botões, mesmo padrão visual em todos. */
@Composable
private fun UpdateDialogShell(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            // ic_splash is a 108dp splash-screen asset — must be sized down explicitly,
            // AlertDialog's icon slot does not scale its content automatically.
            Icon(
                painter = painterResource(R.drawable.ic_splash),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text(title) },
        text = content,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}

@Composable
private fun DownloadProgressContent(versionLabel: String, progress: DownloadProgress?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fraction = progress?.fraction
        if (progress != null && fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(FFTheme.spacing.sm))
            Text("${(fraction * 100).toInt()}% · ${formatBytes(progress.bytesDownloaded)} de ${formatBytes(progress.totalBytes)}")
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(FFTheme.spacing.sm))
            Text("Baixando FlowFuel $versionLabel...")
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(mb).replace('.', ',')
}
