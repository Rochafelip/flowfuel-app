package com.flowfuel.app.feature.vehicle.presentation.odometer

import com.flowfuel.app.core.domain.AppError

data class UpdateOdometerUiState(
    val currentKm: Int = 0,
    val newKm: String = "",
    val regressionError: Boolean = false,
    val isSaving: Boolean = false,
    val formError: AppError? = null,
) {
    val canConfirm: Boolean
        get() = !isSaving && !regressionError && newKm.isNotBlank() && newKm.toIntOrNull() != null
}

sealed interface UpdateOdometerEffect {
    data class NavigateBackWithResult(val updatedKm: Int) : UpdateOdometerEffect
    data object NavigateToLogin : UpdateOdometerEffect
}
