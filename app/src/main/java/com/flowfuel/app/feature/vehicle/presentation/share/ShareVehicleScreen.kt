package com.flowfuel.app.feature.vehicle.presentation.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBrFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

@Composable
fun ShareVehicleScreen(
    onBack: () -> Unit,
    viewModel: ShareVehicleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { FFTopBar(title = "Compartilhar veículo", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FFTheme.spacing.md, vertical = FFTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
        ) {
            when (val s = state) {
                ShareVehicleUiState.Loading -> Text("Carregando...")

                is ShareVehicleUiState.NoShare -> {
                    FFTextField(
                        value = s.email,
                        onValueChange = viewModel::onEmailChange,
                        label = "Email do convidado",
                        errorText = s.error,
                        enabled = !s.isSubmitting,
                    )
                    Text("Duração: ${s.durationDays} dia(s)", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.xs)) {
                        listOf(1, 3, 7, 14, 30).forEach { days ->
                            FFButton(
                                text = "${days}d",
                                onClick = { viewModel.onDurationChange(days) },
                                variant = if (s.durationDays == days) FFButtonVariant.Primary else FFButtonVariant.Secondary,
                                enabled = !s.isSubmitting,
                            )
                        }
                    }
                    FFButton(
                        text = "Enviar convite",
                        onClick = viewModel::sendInvite,
                        enabled = !s.isSubmitting,
                        loading = s.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is ShareVehicleUiState.Pending -> {
                    Text("Convite enviado para ${s.share.guestName.ifBlank { "o convidado" }}, aguardando resposta.")
                    s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    FFButton(
                        text = "Cancelar convite",
                        onClick = viewModel::revokeShare,
                        variant = FFButtonVariant.Destructive,
                        enabled = !s.isRevoking,
                        loading = s.isRevoking,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is ShareVehicleUiState.Active -> {
                    val expiresLabel = s.share.expiresAt?.let {
                        runCatching { LocalDate.parse(it.take(10)).format(ptBrFormatter) }.getOrNull()
                    }
                    Text("Compartilhado com ${s.share.guestName}" + (expiresLabel?.let { " até $it" } ?: ""))
                    s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    FFButton(
                        text = "Encerrar compartilhamento",
                        onClick = viewModel::revokeShare,
                        variant = FFButtonVariant.Destructive,
                        enabled = !s.isRevoking,
                        loading = s.isRevoking,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is ShareVehicleUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
