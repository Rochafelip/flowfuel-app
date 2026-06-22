package com.flowfuel.app.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowfuel.app.core.designsystem.components.FFBottomSheet
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFEmptyState
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFVehicleCard
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.ui.userMessage
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle
import java.text.NumberFormat
import java.util.Locale

/**
 * BottomSheet para trocar o veículo ativo sem sair da Home.
 *
 * Estados tratados: Loading → Skeleton | Success (lista) | Empty | Error.
 */
@Composable
fun VehicleSwitcherBottomSheet(
    state: VehicleSwitcherState,
    onVehicleSelect: (vehicleId: Int) -> Unit,
    onAddVehicle: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    FFBottomSheet(onDismiss = onDismiss) {
        // ── Cabeçalho ────────────────────────────────────────────────────────
        Text(
            text = "Meus veículos",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(FFTheme.spacing.md))

        // ── Conteúdo varia por estado ─────────────────────────────────────
        when (state) {

            // Enquanto carrega: skeleton simples
            VehicleSwitcherState.Idle,
            VehicleSwitcherState.Loading -> {
                Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                    repeat(3) { FFSkeletonBlock(height = 80.dp) }
                }
                Spacer(Modifier.height(FFTheme.spacing.md))
            }

            // Lista disponível
            is VehicleSwitcherState.Success -> {
                if (state.vehicles.isEmpty()) {
                    // Estado vazio
                    FFEmptyState(
                        title = "Você ainda não possui veículos",
                        icon = Icons.Default.DirectionsCar,
                        actionText = "Adicionar veículo",
                        onAction = onAddVehicle,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm)) {
                        state.vehicles.forEach { vehicle ->
                            FFVehicleCard(
                                nickname = vehicleNickname(vehicle),
                                plate = vehicle.licensePlate ?: "—",
                                odometerKm = formatOdometer(vehicle.odometerKm),
                                isActive = vehicle.id == state.activeId,
                                onClick = { onVehicleSelect(vehicle.id) },
                            )
                        }

                        Spacer(Modifier.height(FFTheme.spacing.sm))

                        FFButton(
                            text = "Adicionar veículo",
                            onClick = onAddVehicle,
                            variant = FFButtonVariant.Tonal,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = Icons.Default.DirectionsCar,
                        )
                    }
                }
                Spacer(Modifier.height(FFTheme.spacing.md))
            }

            // Erro ao buscar lista
            is VehicleSwitcherState.Error -> {
                FFErrorState(
                    message = state.error.userMessage(),
                    onRetry = onRetry,
                )
                Spacer(Modifier.height(FFTheme.spacing.md))
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun vehicleNickname(vehicle: Vehicle): String =
    buildString {
        append(vehicle.brand)
        append(" ")
        append(vehicle.model)
        vehicle.modelYear?.let { append(" ($it)") }
    }

private val kmFormat: NumberFormat
    get() = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

private fun formatOdometer(km: Int): String = kmFormat.format(km)
