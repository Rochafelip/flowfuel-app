package com.flowfuel.app.feature.vehicleevent.presentation.list

import com.flowfuel.app.core.domain.AppError
import com.flowfuel.app.core.pagination.PaginationState
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import com.flowfuel.app.feature.vehicleevent.domain.model.EventDateFilter
import com.flowfuel.app.feature.vehicleevent.domain.model.VehicleEvent

sealed interface VehicleEventsScreenState {
    data object Loading : VehicleEventsScreenState
    data class Success(val events: List<VehicleEvent>) : VehicleEventsScreenState
    data class Error(val error: AppError) : VehicleEventsScreenState
    data object Empty : VehicleEventsScreenState
}

data class VehicleEventsUiState(
    val screenState: VehicleEventsScreenState = VehicleEventsScreenState.Loading,
    val isRefreshing: Boolean = false,
    val pagination: PaginationState = PaginationState(),
    val selectedCategory: EventCategory? = null,
    val selectedDateFilter: EventDateFilter = EventDateFilter.All,
    val activeVehicleLabel: String? = null,
)

sealed interface VehicleEventsEffect {
    data class NavigateToCreate(val vehicleId: Int) : VehicleEventsEffect
    data class NavigateToDetails(val eventId: Int) : VehicleEventsEffect
    data object NavigateToLogin : VehicleEventsEffect
}
