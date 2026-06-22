package com.flowfuel.app.feature.vehicle.presentation.details

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle

// ─── Estado da tela ───────────────────────────────────────────────────────────

sealed interface VehicleDetailsScreenState {
    data object Loading : VehicleDetailsScreenState
    data class Success(val vehicle: Vehicle) : VehicleDetailsScreenState
    data class Error(val error: AppError) : VehicleDetailsScreenState
}

// ─── Estado global ────────────────────────────────────────────────────────────

data class VehicleDetailsUiState(
    val screenState: VehicleDetailsScreenState = VehicleDetailsScreenState.Loading,
    /** true enquanto pull-to-refresh está em andamento (não shimmer) */
    val isRefreshing: Boolean = false,
)

// ─── Efeitos pontuais ─────────────────────────────────────────────────────────

sealed interface VehicleDetailsEffect {
    data class NavigateToEdit(val vehicleId: Int) : VehicleDetailsEffect
    data class NavigateToUpdateOdometer(val vehicleId: Int, val currentKm: Int) : VehicleDetailsEffect
    data class NavigateToEvents(val vehicleId: Int) : VehicleDetailsEffect
    data object NavigateBack : VehicleDetailsEffect
    data object NavigateToLogin : VehicleDetailsEffect
}
