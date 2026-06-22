package com.flowfuel.app.feature.vehicleevent.presentation.edit

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory

sealed interface EditVehicleEventScreenState {
    data object Loading : EditVehicleEventScreenState
    data class Editing(
        val category: EventCategory,
        val title: String,
        val description: String,
        val amount: String,
        val eventDate: String,
        val odometerKm: String,
        val notes: String,
        val titleError: String? = null,
        val amountError: String? = null,
        val eventDateError: String? = null,
        val odometerError: String? = null,
        val isDirty: Boolean = false,
        val isSubmitting: Boolean = false,
        val showDiscardDialog: Boolean = false,
        val formError: AppError? = null,
    ) : EditVehicleEventScreenState
    data class Error(val error: AppError) : EditVehicleEventScreenState
}

data class EditVehicleEventUiState(
    val screenState: EditVehicleEventScreenState = EditVehicleEventScreenState.Loading,
)

sealed interface EditVehicleEventEffect {
    data class ShowSnackbar(val message: String) : EditVehicleEventEffect
    data object NavigateBack : EditVehicleEventEffect
    data object NavigateToLogin : EditVehicleEventEffect
}
