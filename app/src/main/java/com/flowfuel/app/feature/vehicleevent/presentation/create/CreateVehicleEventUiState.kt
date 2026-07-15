package com.flowfuel.app.feature.vehicleevent.presentation.create

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory

data class CreateVehicleEventUiState(
    val category: EventCategory = EventCategory.OTHER,
    val availableCategories: List<EventCategory> = EventCategory.entries,
    val title: String = "",
    val description: String = "",
    val amount: String = "",
    val eventDate: String = "",
    val odometerKm: String = "",
    val notes: String = "",
    val titleError: String? = null,
    val amountError: String? = null,
    val eventDateError: String? = null,
    val odometerError: String? = null,
    val isSubmitting: Boolean = false,
    val isDirty: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val formError: AppError? = null,
)

sealed interface CreateVehicleEventEffect {
    data class ShowSnackbar(val message: String) : CreateVehicleEventEffect
    data object NavigateBack : CreateVehicleEventEffect
    data object NavigateToLogin : CreateVehicleEventEffect
}
