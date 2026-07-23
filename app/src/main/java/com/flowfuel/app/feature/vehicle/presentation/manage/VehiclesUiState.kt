package com.flowfuel.app.feature.vehicle.presentation.manage

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.vehicleshare.domain.model.VehicleShare
import com.flowfuel.app.feature.vehicle.domain.model.Vehicle

// ─── Estado da tela ───────────────────────────────────────────────────────────

sealed interface VehiclesScreenState {
    data object Loading : VehiclesScreenState
    data object Empty : VehiclesScreenState
    data class Error(val error: AppError) : VehiclesScreenState
    data class Success(
        val ownedItems: List<Vehicle>,
        val borrowedItems: List<VehicleShare>,
    ) : VehiclesScreenState
}

// ─── Estado global ────────────────────────────────────────────────────────────

data class VehiclesUiState(
    val screenState: VehiclesScreenState = VehiclesScreenState.Loading,
    /** ID do veículo atualmente ativo; atualizado otimisticamente ao trocar. */
    val activeVehicleId: Int? = null,
    /** Não-null enquanto o dialog de confirmação de exclusão está visível. */
    val vehiclePendingDelete: Vehicle? = null,
    /** true enquanto a chamada de exclusão está em andamento. */
    val isDeleting: Boolean = false,
)

// ─── Efeitos pontuais ─────────────────────────────────────────────────────────

sealed interface VehiclesEffect {
    data object NavigateToLogin : VehiclesEffect
    data class NavigateToGuestVehicle(val share: VehicleShare) : VehiclesEffect
}
