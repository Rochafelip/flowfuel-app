package com.flowfuel.app.feature.vehicle.presentation.guest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFNumberField
import com.flowfuel.app.core.designsystem.components.FFNumberKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBrFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))

@Composable
fun GuestVehicleScreen(
    guestVehicle: VehicleShare,
    onNavigateToCreateEvent: (vehicleId: Int) -> Unit,
    onNavigateToPicker: (message: String?) -> Unit,
    onSwitchVehicleClicked: () -> Unit,
    viewModel: GuestVehicleViewModel = hiltViewModel(),
) {
    viewModel.initialize(guestVehicle)
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                GuestVehicleEffect.OdometerUpdated ->
                    snackbarHostState.showSnackbar(FFSnackbarVisuals("Odômetro atualizado", FFSnackbarKind.Success))
                is GuestVehicleEffect.NavigateToCreateEvent -> onNavigateToCreateEvent(effect.vehicleId)
                is GuestVehicleEffect.NavigateToPicker -> onNavigateToPicker(effect.message)
            }
        }
    }

    Scaffold(
        topBar = { FFTopBar(title = "${state.vehicleBrand} ${state.vehicleModel}") },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FFTheme.spacing.md, vertical = FFTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.lg),
        ) {
            val expiresLabel = state.expiresAt?.let {
                runCatching { LocalDate.parse(it.take(10)).format(ptBrFormatter) }.getOrNull()
            }
            Text(
                text = "Veículo emprestado" + (expiresLabel?.let { " até $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FFNumberField(
                value = state.odometerInput,
                onValueChange = viewModel::onOdometerChange,
                label = "Atualizar odômetro (km)",
                kind = FFNumberKind.WholeNumber,
                errorText = state.odometerError,
                enabled = !state.isSavingOdometer,
                modifier = Modifier.fillMaxWidth(),
            )
            FFButton(
                text = "Salvar odômetro",
                onClick = viewModel::confirmOdometer,
                enabled = !state.isSavingOdometer,
                loading = state.isSavingOdometer,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FFTheme.spacing.md))

            FFButton(
                text = "Registrar abastecimento/despesa",
                onClick = viewModel::onCreateEventClicked,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FFTheme.spacing.xl))

            FFButton(
                text = "Trocar de veículo",
                onClick = onSwitchVehicleClicked,
                variant = FFButtonVariant.Text,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
