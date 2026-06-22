package com.flowfuel.app.feature.vehicleevent.presentation.details

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent

sealed interface VehicleEventDetailsScreenState {
    data object Loading : VehicleEventDetailsScreenState
    data class Success(val event: VehicleEvent) : VehicleEventDetailsScreenState
    data class Error(val error: AppError) : VehicleEventDetailsScreenState
    data object NotFound : VehicleEventDetailsScreenState
}

data class VehicleEventDetailsUiState(
    val screenState: VehicleEventDetailsScreenState = VehicleEventDetailsScreenState.Loading,
    val isRefreshing: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteDialog: Boolean = false,
)

sealed interface VehicleEventDetailsEffect {
    data class NavigateToEdit(val eventId: Int) : VehicleEventDetailsEffect
    data object NavigateBack : VehicleEventDetailsEffect
    data class ShowSnackbar(val message: String) : VehicleEventDetailsEffect
    data object NavigateToLogin : VehicleEventDetailsEffect
}
