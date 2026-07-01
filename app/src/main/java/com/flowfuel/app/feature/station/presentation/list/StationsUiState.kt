package com.flowfuel.app.feature.station.presentation.list

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.feature.station.domain.model.Station

// ─── Estado da tela ───────────────────────────────────────────────────────────

sealed interface StationsUiState {
    data object Loading : StationsUiState
    data class Success(val stations: List<Station>) : StationsUiState
    data object Empty : StationsUiState
    data class Error(val error: AppError) : StationsUiState
    /** GPS desligado ou localização indisponível — permissão já concedida. */
    data object LocationUnavailable : StationsUiState
    /** Permissão de localização ainda não concedida (ou negada). */
    data object PermissionRequired : StationsUiState
}

// ─── Efeitos pontuais ─────────────────────────────────────────────────────────

sealed interface StationsEffect {
    data class OpenNavigation(val uri: String) : StationsEffect
    data object NavigateToLogin : StationsEffect
}
